package io.github.jackdaw16.learningplatform.certificate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.certificate.application.port.CertificateRepository;
import io.github.jackdaw16.learningplatform.certificate.domain.Certificate;
import io.github.jackdaw16.learningplatform.certificate.infrastructure.rabbitmq.CertificateEnrollmentCompletedListener;
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentCommand;
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentResult;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentCreationService;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentProgressService;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCompletedEventV1;
import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.IntegrationEvent;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence.PostgresOutboxPublicationWorker;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentProcessor;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import io.github.jackdaw16.learningplatform.payment.domain.PaymentStatus;
import io.github.jackdaw16.learningplatform.shared.Money;
import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import io.github.jackdaw16.learningplatform.student.domain.Student;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "messaging.outbox.poll-interval=1h",
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(CertificateWorkflowIntegrationTest.CertificateTestConfiguration.class)
class CertificateWorkflowIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final long POLL_NANOS = Duration.ofMillis(25).toNanos();
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-30T12:00:00.123456Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-alpine")
            .withAdminUser("test")
            .withAdminPassword("test");

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateEnrollmentCompletedListener certificateListener;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EnrollmentCreationService enrollmentCreationService;

    @Autowired
    private EnrollmentProgressService enrollmentProgressService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PostgresOutboxPublicationWorker outboxPublicationWorker;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CountingPaymentProcessor paymentProcessor;

    private UUID categoryId;
    private UUID instructorId;

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.addresses", RABBITMQ::getAmqpUrl);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE processed_events, outbox_events, certificates, payments, enrollments, students, courses, "
                + "categories, instructors");
        purgeQueues();
        paymentProcessor.reset();
        categoryId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        categoryRepository.save(new Category(categoryId, "Certificates", null));
        instructorRepository.save(new Instructor(instructorId, "Ada Lovelace", "ada.certificates@example.com", null));
    }

    @Test
    void createsOneCertificateForAnAsynchronousCompletedEnrollmentEvent() {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.COMPLETED);
        EnrollmentCompletedEventV1 event = completedEvent(enrollment, UUID.randomUUID());

        publish(event);

        await(() -> certificateCount() == 1);
        Certificate certificate = certificateRepository.findByEnrollmentId(enrollment.id()).orElseThrow();
        assertEquals(enrollment.id(), certificate.enrollmentId());
        assertFalse(certificate.verificationCode().isBlank());
        assertDoesNotThrow(() -> UUID.fromString(certificate.verificationCode()));
        assertEquals(FIXED_INSTANT, certificate.issuedAt());
        assertEquals(certificate, certificateRepository.findByVerificationCode(certificate.verificationCode()).orElseThrow());
        assertEquals(1, processedEventCount());
    }

    @Test
    void treatsSameAndDifferentEventIdsForTheSameEnrollmentAsCertificateNoOps() {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.COMPLETED);
        EnrollmentCompletedEventV1 original = completedEvent(enrollment, UUID.randomUUID());

        publish(original);
        await(() -> certificateCount() == 1 && processedEventCount() == 1);
        Certificate certificate = certificateRepository.findByEnrollmentId(enrollment.id()).orElseThrow();

        publish(original);
        await(() -> queueIsEmpty(RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_QUEUE));
        assertEquals(1, certificateCount());
        assertEquals(1, processedEventCount());

        publish(completedEvent(enrollment, UUID.randomUUID()));
        await(() -> processedEventCount() == 2);
        assertEquals(1, certificateCount());
        assertEquals(certificate, certificateRepository.findByEnrollmentId(enrollment.id()).orElseThrow());
    }

    @Test
    void concurrentListenerDeliveriesForOneEnrollmentCreateExactlyOneCertificate() throws Exception {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.COMPLETED);
        EnrollmentCompletedEventV1 first = completedEvent(enrollment, UUID.randomUUID());
        EnrollmentCompletedEventV1 second = completedEvent(enrollment, UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Void>> futures = List.of(
                    executor.submit(listenerDelivery(first, ready, start)),
                    executor.submit(listenerDelivery(second, ready, start))
            );
            assertTrue(ready.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "Listener deliveries did not become ready");
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, certificateCount());
        assertEquals(2, processedEventCount());
        assertEquals(enrollment.id(), certificateRepository.findByEnrollmentId(enrollment.id()).orElseThrow().enrollmentId());
    }

    @Test
    void rejectsStudentAndCourseMismatchesToTheDedicatedCertificateDeadLetterQueue() {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.COMPLETED);
        EnrollmentCompletedEventV1 studentMismatch = new EnrollmentCompletedEventV1(
                metadata(UUID.randomUUID()), enrollment.id(), UUID.randomUUID(), enrollment.courseId()
        );
        EnrollmentCompletedEventV1 courseMismatch = new EnrollmentCompletedEventV1(
                metadata(UUID.randomUUID()), enrollment.id(), enrollment.studentId(), UUID.randomUUID()
        );

        publish(studentMismatch);
        assertNotNull(awaitDeadLetter());
        publish(courseMismatch);
        assertNotNull(awaitDeadLetter());

        assertEquals(0, certificateCount());
        assertEquals(0, processedEventCount());
    }

    @Test
    void rejectsNonCompletedEnrollmentsToTheDedicatedCertificateDeadLetterQueue() {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.ACTIVE);

        publish(completedEvent(enrollment, UUID.randomUUID()));

        assertNotNull(awaitDeadLetter());
        assertEquals(0, certificateCount());
        assertEquals(0, processedEventCount());
    }

    @Test
    void invalidMessagesCreateNeitherCertificateNorProcessedEvent() {
        Message invalid = MessageBuilder.withBody("not-json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(UUID.randomUUID().toString())
                .setHeader("eventType", EnrollmentCompletedEventV1.EVENT_TYPE)
                .setHeader("eventVersion", EnrollmentCompletedEventV1.VERSION)
                .build();

        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.ENROLLMENT_COMPLETED_ROUTING_KEY, invalid);

        assertNotNull(awaitDeadLetter());
        assertEquals(0, certificateCount());
        assertEquals(0, processedEventCount());
    }

    @Test
    void completesTheFullOutboxDrivenEnrollmentToCertificateWorkflowOnce() {
        CreateEnrollmentResult result = createEnrollment();

        publishUntil(() -> enrollmentStatus(result.enrollment().id()) == EnrollmentStatus.ACTIVE);
        assertEquals(PaymentStatus.CONFIRMED, paymentStatus(result.payment().id()));

        Enrollment completed = enrollmentProgressService.updateProgress(result.enrollment().id(), 100);
        publishUntil(() -> certificateCount() == 1);

        Certificate certificate = certificateRepository.findByEnrollmentId(result.enrollment().id()).orElseThrow();
        assertEquals(EnrollmentStatus.COMPLETED, completed.status());
        assertEquals(100, completed.progress());
        assertEquals(FIXED_INSTANT, completed.completedAt());
        assertEquals(EnrollmentStatus.COMPLETED, enrollmentStatus(result.enrollment().id()));
        assertEquals(100, enrollmentProgress(result.enrollment().id()));
        assertEquals(FIXED_INSTANT, enrollmentCompletedAt(result.enrollment().id()));
        assertEquals(result.enrollment().id(), certificate.enrollmentId());
        assertFalse(certificate.verificationCode().isBlank());
        assertEquals(FIXED_INSTANT, certificate.issuedAt());
        assertEquals(1, certificateCount());
        assertEquals(1, paymentProcessor.invocations());
        assertEquals(3, processedEventCount());
        assertEquals(3, publishedOutboxCount());
    }

    private Callable<Void> listenerDelivery(
            EnrollmentCompletedEventV1 event,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Concurrent listener deliveries did not start");
            }
            certificateListener.onMessage(messageFor(event));
            return null;
        };
    }

    private Enrollment saveEnrollment(EnrollmentStatus status) {
        Student student = new Student(
                UUID.randomUUID(),
                "Student",
                "Example",
                "student-" + UUID.randomUUID() + "@example.com",
                FIXED_INSTANT
        );
        studentRepository.save(student);
        Course course = new Course(
                UUID.randomUUID(),
                "Certificate course " + UUID.randomUUID(),
                null,
                8,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("19.99"), Currency.getInstance("USD")),
                10,
                categoryId,
                instructorId
        );
        course.publish();
        courseRepository.save(course);
        Enrollment enrollment = new Enrollment(UUID.randomUUID(), student.id(), course.id(), FIXED_INSTANT);
        if (status != EnrollmentStatus.PENDING_PAYMENT) {
            enrollment.activate();
            if (status == EnrollmentStatus.COMPLETED) {
                enrollment.updateProgress(100, FIXED_INSTANT);
            }
        }
        enrollmentRepository.save(enrollment);
        return enrollment;
    }

    private CreateEnrollmentResult createEnrollment() {
        Student student = new Student(
                UUID.randomUUID(),
                "Student",
                "Example",
                "student-" + UUID.randomUUID() + "@example.com",
                FIXED_INSTANT
        );
        studentRepository.save(student);
        Course course = new Course(
                UUID.randomUUID(),
                "End-to-end certificate course " + UUID.randomUUID(),
                null,
                8,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("19.99"), Currency.getInstance("USD")),
                1,
                categoryId,
                instructorId
        );
        course.publish();
        courseRepository.save(course);
        return enrollmentCreationService.create(new CreateEnrollmentCommand(
                student.id(), course.id(), "certificate-" + UUID.randomUUID()
        ));
    }

    private EnrollmentCompletedEventV1 completedEvent(Enrollment enrollment, UUID eventId) {
        return new EnrollmentCompletedEventV1(metadata(eventId), enrollment.id(), enrollment.studentId(), enrollment.courseId());
    }

    private EventMetadataV1 metadata(UUID eventId) {
        return new EventMetadataV1(
                eventId,
                EnrollmentCompletedEventV1.EVENT_TYPE,
                EnrollmentCompletedEventV1.VERSION,
                FIXED_INSTANT
        );
    }

    private void publish(IntegrationEvent event) {
        rabbitTemplate.send(
                RabbitTopology.EVENTS_EXCHANGE,
                RabbitTopology.ENROLLMENT_COMPLETED_ROUTING_KEY,
                messageFor(event)
        );
    }

    private Message messageFor(IntegrationEvent event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setMessageId(event.metadata().eventId().toString());
            properties.setHeader("eventType", event.metadata().eventType());
            properties.setHeader("eventVersion", event.metadata().version());
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return new Message(objectMapper.writeValueAsBytes(event), properties);
        } catch (Exception exception) {
            throw new AssertionError("Could not serialize integration event", exception);
        }
    }

    private Message awaitDeadLetter() {
        Message message = rabbitTemplate.receive(RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_DLQ, TIMEOUT.toMillis());
        assertNotNull(message, "Expected a message in the dedicated certificate DLQ");
        return message;
    }

    private void publishUntil(BooleanSupplier condition) {
        await(() -> {
            outboxPublicationWorker.publishPending();
            return condition.getAsBoolean();
        });
    }

    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(POLL_NANOS);
        }
        fail("Timed out waiting for certificate workflow completion");
    }

    private void purgeQueues() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE);
            channel.queuePurge(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_QUEUE);
            channel.queuePurge(RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_QUEUE);
            channel.queuePurge(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ);
            channel.queuePurge(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLQ);
            channel.queuePurge(RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_DLQ);
            return null;
        });
    }

    private boolean queueIsEmpty(String queue) {
        return rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queue).getMessageCount() == 0);
    }

    private int certificateCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM certificates", Integer.class);
    }

    private int processedEventCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events", Integer.class);
    }

    private int publishedOutboxCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events WHERE status = 'PUBLISHED'", Integer.class);
    }

    private EnrollmentStatus enrollmentStatus(UUID enrollmentId) {
        return EnrollmentStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM enrollments WHERE id = ?", String.class, enrollmentId
        ));
    }

    private int enrollmentProgress(UUID enrollmentId) {
        return jdbcTemplate.queryForObject("SELECT progress FROM enrollments WHERE id = ?", Integer.class, enrollmentId);
    }

    private Instant enrollmentCompletedAt(UUID enrollmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT completed_at FROM enrollments WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp("completed_at").toInstant(),
                enrollmentId
        );
    }

    private PaymentStatus paymentStatus(UUID paymentId) {
        return PaymentStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE id = ?", String.class, paymentId
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CertificateTestConfiguration {

        @Bean
        @Primary
        Clock certificateTestClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        CountingPaymentProcessor paymentProcessor() {
            return new CountingPaymentProcessor();
        }
    }

    static final class CountingPaymentProcessor implements PaymentProcessor {

        private int invocations;

        @Override
        public synchronized io.github.jackdaw16.learningplatform.payment.application.port.PaymentOutcome process(Payment payment) {
            invocations++;
            return io.github.jackdaw16.learningplatform.payment.application.port.PaymentOutcome.CONFIRMED;
        }

        synchronized int invocations() {
            return invocations;
        }

        synchronized void reset() {
            invocations = 0;
        }
    }
}
