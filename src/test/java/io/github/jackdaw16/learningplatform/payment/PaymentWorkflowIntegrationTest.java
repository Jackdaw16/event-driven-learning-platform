package io.github.jackdaw16.learningplatform.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentCommand;
import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentResult;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentCancellationService;
import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentCreationService;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCreatedEventV1;
import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.IntegrationEvent;
import io.github.jackdaw16.learningplatform.messaging.PaymentConfirmedEventV1;
import io.github.jackdaw16.learningplatform.messaging.PaymentFailedEventV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence.PostgresIntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence.PostgresOutboxPublicationWorker;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentOutcome;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentProcessor;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import io.github.jackdaw16.learningplatform.payment.domain.PaymentStatus;
import io.github.jackdaw16.learningplatform.shared.Money;
import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import io.github.jackdaw16.learningplatform.student.domain.Student;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
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
@Import(PaymentWorkflowIntegrationTest.PaymentWorkflowTestConfiguration.class)
class PaymentWorkflowIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_NANOS = Duration.ofMillis(25).toNanos();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-alpine")
            .withAdminUser("test")
            .withAdminPassword("test");

    @Autowired
    private EnrollmentCreationService enrollmentCreationService;

    @Autowired
    private EnrollmentCancellationService enrollmentCancellationService;

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
    private ControllablePaymentProcessor paymentProcessor;

    @Autowired
    private FailingIntegrationEventRecorder integrationEventRecorder;

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
        integrationEventRecorder.reset();
        categoryId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        categoryRepository.save(new Category(categoryId, "Payments", null));
        instructorRepository.save(new Instructor(instructorId, "Ada Lovelace", "ada.payments@example.com", null));
    }

    @Test
    void confirmsPaymentPublishesOutcomeAndActivatesEnrollment() {
        CreateEnrollmentResult result = createEnrollment();
        paymentProcessor.setOutcome(PaymentOutcome.CONFIRMED);

        publishUntil(() -> enrollmentStatus(result.enrollment().id()) == EnrollmentStatus.ACTIVE);

        assertEquals(PaymentStatus.CONFIRMED, paymentStatus(result.payment().id()));
        assertEquals(1, occupiedSeats(result.enrollment().courseId()));
        assertEquals(2, publishedOutboxCount());
        assertEquals(2, processedEventCount());
        assertEquals(1, paymentProcessor.invocations());
        assertEquals(RabbitTopology.PAYMENT_CONFIRMED_ROUTING_KEY,
                routingKeyFor("payment.confirmed.v1"));
    }

    @Test
    void failsPaymentPublishesOutcomeCancelsEnrollmentAndReleasesOneSeat() throws Exception {
        CreateEnrollmentResult result = createEnrollment();
        paymentProcessor.setOutcome(PaymentOutcome.FAILED);

        publishUntil(() -> enrollmentStatus(result.enrollment().id()) == EnrollmentStatus.CANCELLED);

        assertEquals(PaymentStatus.FAILED, paymentStatus(result.payment().id()));
        assertEquals(0, occupiedSeats(result.enrollment().courseId()));
        assertEquals(2, publishedOutboxCount());
        assertEquals(2, processedEventCount());
        assertEquals(RabbitTopology.PAYMENT_FAILED_ROUTING_KEY, routingKeyFor("payment.failed.v1"));

        PaymentFailedEventV1 original = objectMapper.readValue(
                payloadFor("payment.failed.v1"), PaymentFailedEventV1.class
        );
        PaymentFailedEventV1 repeatedWithNewId = new PaymentFailedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        PaymentFailedEventV1.EVENT_TYPE,
                        PaymentFailedEventV1.VERSION,
                        Instant.now()
                ),
                original.paymentId(),
                original.enrollmentId()
        );
        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.PAYMENT_FAILED_ROUTING_KEY,
                messageFor(repeatedWithNewId));

        await(() -> processedEventCount() == 3);
        assertEquals(EnrollmentStatus.CANCELLED, enrollmentStatus(result.enrollment().id()));
        assertEquals(0, occupiedSeats(result.enrollment().courseId()));
        assertEquals(3, processedEventCount());
        assertEquals(2, outboxCount());
    }

    @Test
    void cancellationBeforeDelayedPaymentProcessingKeepsConfirmedPaymentAndCancelledEnrollment() {
        CreateEnrollmentResult result = createEnrollment();
        paymentProcessor.setOutcome(PaymentOutcome.CONFIRMED);

        enrollmentCancellationService.cancel(result.enrollment().id());
        publishUntil(() -> processedEventCount() == 2);

        assertEquals(PaymentStatus.CONFIRMED, paymentStatus(result.payment().id()));
        assertEquals(EnrollmentStatus.CANCELLED, enrollmentStatus(result.enrollment().id()));
        assertEquals(0, occupiedSeats(result.enrollment().courseId()));
        assertEquals(0, queueMessageCount(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLQ));
    }

    @Test
    void cancellationAfterPaymentConfirmationAcknowledgesDelayedActivationWithoutReleasingTwice() {
        CreateEnrollmentResult result = createEnrollment();
        markPaymentStatus(result.payment().id(), PaymentStatus.CONFIRMED);

        enrollmentCancellationService.cancel(result.enrollment().id());
        PaymentConfirmedEventV1 delayedEvent = new PaymentConfirmedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        PaymentConfirmedEventV1.EVENT_TYPE,
                        PaymentConfirmedEventV1.VERSION,
                        Instant.now()
                ),
                result.payment().id(),
                result.enrollment().id()
        );
        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.PAYMENT_CONFIRMED_ROUTING_KEY,
                messageFor(delayedEvent));

        await(() -> processedEventCount() == 1);
        assertEquals(PaymentStatus.CONFIRMED, paymentStatus(result.payment().id()));
        assertEquals(EnrollmentStatus.CANCELLED, enrollmentStatus(result.enrollment().id()));
        assertEquals(0, occupiedSeats(result.enrollment().courseId()));
        assertEquals(0, queueMessageCount(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLQ));
    }

    @Test
    void rejectsConfirmedPaymentForAnotherEnrollmentToTheDeadLetterQueueWithoutMutatingTheTarget() {
        CreateEnrollmentResult target = createEnrollment();
        CreateEnrollmentResult paymentOwner = createEnrollment();
        markPaymentStatus(paymentOwner.payment().id(), PaymentStatus.CONFIRMED);
        PaymentConfirmedEventV1 invalidEvent = new PaymentConfirmedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        PaymentConfirmedEventV1.EVENT_TYPE,
                        PaymentConfirmedEventV1.VERSION,
                        Instant.now()
                ),
                paymentOwner.payment().id(),
                target.enrollment().id()
        );

        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.PAYMENT_CONFIRMED_ROUTING_KEY,
                messageFor(invalidEvent));
        Message deadLetter = awaitMessage(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLQ);

        assertNotNull(deadLetter);
        assertEquals(EnrollmentStatus.PENDING_PAYMENT, enrollmentStatus(target.enrollment().id()));
        assertEquals(1, occupiedSeats(target.enrollment().courseId()));
        assertEquals(0, processedEventCount());
    }

    @Test
    void rejectsFailedPaymentForAnotherEnrollmentToTheDeadLetterQueueWithoutMutatingTheTarget() {
        CreateEnrollmentResult target = createEnrollment();
        CreateEnrollmentResult paymentOwner = createEnrollment();
        markPaymentStatus(paymentOwner.payment().id(), PaymentStatus.FAILED);
        PaymentFailedEventV1 invalidEvent = new PaymentFailedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        PaymentFailedEventV1.EVENT_TYPE,
                        PaymentFailedEventV1.VERSION,
                        Instant.now()
                ),
                paymentOwner.payment().id(),
                target.enrollment().id()
        );

        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.PAYMENT_FAILED_ROUTING_KEY,
                messageFor(invalidEvent));
        Message deadLetter = awaitMessage(RabbitTopology.ENROLLMENT_PAYMENT_FAILED_DLQ);

        assertNotNull(deadLetter);
        assertEquals(EnrollmentStatus.PENDING_PAYMENT, enrollmentStatus(target.enrollment().id()));
        assertEquals(1, occupiedSeats(target.enrollment().courseId()));
        assertEquals(0, processedEventCount());
    }

    @Test
    void redeliveryWithTheSameEventIdAppliesPaymentEffectOnlyOnce() {
        CreateEnrollmentResult result = createEnrollment();
        paymentProcessor.setOutcome(PaymentOutcome.CONFIRMED);

        publishUntil(() -> enrollmentStatus(result.enrollment().id()) == EnrollmentStatus.ACTIVE);
        EnrollmentCreatedEventV1 original = readEnrollmentCreatedEvent();
        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY,
                messageFor(original));

        await(() -> queueMessageCount(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE) == 0);
        assertEquals(1, paymentProcessor.invocations());
        assertEquals(PaymentStatus.CONFIRMED, paymentStatus(result.payment().id()));
        assertEquals(2, outboxCount());
        assertEquals(2, processedEventCount());
    }

    @Test
    void rollsBackPaymentMutationProcessedEventAndOutcomeOutboxWhenRecordingFails() {
        CreateEnrollmentResult result = createEnrollment();
        paymentProcessor.setOutcome(PaymentOutcome.CONFIRMED);
        integrationEventRecorder.failOutcomeEvents();

        outboxPublicationWorker.publishPending();
        Message deadLetter = awaitMessage(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ);

        assertNotNull(deadLetter);
        assertEquals(PaymentStatus.PENDING, paymentStatus(result.payment().id()));
        assertEquals(EnrollmentStatus.PENDING_PAYMENT, enrollmentStatus(result.enrollment().id()));
        assertEquals(1, publishedOutboxCount());
        assertEquals(1, outboxCount());
        assertEquals(0, processedEventCount());
    }

    @Test
    void rejectsPayloadAndAmqpMetadataMismatchToTheDeadLetterQueue() throws Exception {
        EnrollmentCreatedEventV1 payload = new EnrollmentCreatedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        EnrollmentCreatedEventV1.EVENT_TYPE,
                        EnrollmentCreatedEventV1.VERSION,
                        Instant.now()
                ),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("19.99"),
                "USD"
        );
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(payload.metadata().eventId().toString());
        properties.setHeader("eventType", PaymentFailedEventV1.EVENT_TYPE);
        properties.setHeader("eventVersion", PaymentFailedEventV1.VERSION);
        rabbitTemplate.send(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE,
                new Message(objectMapper.writeValueAsBytes(payload), properties));

        Message deadLetter = awaitMessage(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ);

        assertNotNull(deadLetter);
        assertEquals(0, processedEventCount());
        assertEquals(0, outboxCount());
    }

    private CreateEnrollmentResult createEnrollment() {
        Student student = new Student(
                UUID.randomUUID(),
                "Student",
                "Example",
                "student-" + UUID.randomUUID() + "@example.com",
                Instant.now()
        );
        studentRepository.save(student);
        Course course = new Course(
                UUID.randomUUID(),
                "Payment course " + UUID.randomUUID(),
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
        return enrollmentCreationService.create(new CreateEnrollmentCommand(student.id(), course.id(), "payment-" + UUID.randomUUID()));
    }

    private EnrollmentCreatedEventV1 readEnrollmentCreatedEvent() {
        try {
            return objectMapper.readValue(payloadFor(EnrollmentCreatedEventV1.EVENT_TYPE), EnrollmentCreatedEventV1.class);
        } catch (Exception exception) {
            throw new AssertionError("Could not read enrollment-created payload", exception);
        }
    }

    private Message messageFor(IntegrationEvent event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setMessageId(event.metadata().eventId().toString());
            properties.setHeader("eventType", event.metadata().eventType());
            properties.setHeader("eventVersion", event.metadata().version());
            properties.setContentType("application/json");
            return new Message(objectMapper.writeValueAsBytes(event), properties);
        } catch (Exception exception) {
            throw new AssertionError("Could not serialize integration event", exception);
        }
    }

    private void publishUntil(BooleanSupplier condition) {
        await(() -> {
            outboxPublicationWorker.publishPending();
            return condition.getAsBoolean();
        });
    }

    private Message awaitMessage(String queue) {
        AtomicReference<Message> message = new AtomicReference<>();
        await(() -> {
            message.set(rabbitTemplate.receive(queue, 100));
            return message.get() != null;
        });
        return message.get();
    }

    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(POLL_NANOS);
        }
        fail("Timed out waiting for asynchronous payment workflow completion");
    }

    private void purgeQueues() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE);
            channel.queuePurge(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_QUEUE);
            channel.queuePurge(RabbitTopology.ENROLLMENT_PAYMENT_FAILED_QUEUE);
            channel.queuePurge(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ);
            channel.queuePurge(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLQ);
            channel.queuePurge(RabbitTopology.ENROLLMENT_PAYMENT_FAILED_DLQ);
            return null;
        });
    }

    private EnrollmentStatus enrollmentStatus(UUID enrollmentId) {
        return EnrollmentStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM enrollments WHERE id = ?", String.class, enrollmentId
        ));
    }

    private PaymentStatus paymentStatus(UUID paymentId) {
        return PaymentStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE id = ?", String.class, paymentId
        ));
    }

    private void markPaymentStatus(UUID paymentId, PaymentStatus status) {
        jdbcTemplate.update("UPDATE payments SET status = ? WHERE id = ?", status.name(), paymentId);
    }

    private int occupiedSeats(UUID courseId) {
        return jdbcTemplate.queryForObject(
                "SELECT occupied_seats FROM courses WHERE id = ?", Integer.class, courseId
        );
    }

    private int processedEventCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events", Integer.class);
    }

    private int outboxCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
    }

    private int publishedOutboxCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events WHERE status = 'PUBLISHED'", Integer.class);
    }

    private String payloadFor(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE event_type = ?", String.class, eventType
        );
    }

    private String routingKeyFor(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT routing_key FROM outbox_events WHERE event_type = ?", String.class, eventType
        );
    }

    private int queueMessageCount(String queue) {
        return rabbitTemplate.execute(channel -> (int) channel.messageCount(queue));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentWorkflowTestConfiguration {

        @Bean
        @Primary
        ControllablePaymentProcessor paymentProcessor() {
            return new ControllablePaymentProcessor();
        }

        @Bean
        @Primary
        FailingIntegrationEventRecorder integrationEventRecorder(PostgresIntegrationEventRecorder delegate) {
            return new FailingIntegrationEventRecorder(delegate);
        }
    }

    static final class ControllablePaymentProcessor implements PaymentProcessor {

        private final AtomicReference<PaymentOutcome> outcome = new AtomicReference<>(PaymentOutcome.CONFIRMED);
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public PaymentOutcome process(Payment payment) {
            invocations.incrementAndGet();
            return outcome.get();
        }

        void setOutcome(PaymentOutcome outcome) {
            this.outcome.set(outcome);
        }

        int invocations() {
            return invocations.get();
        }

        void reset() {
            outcome.set(PaymentOutcome.CONFIRMED);
            invocations.set(0);
        }
    }

    static final class FailingIntegrationEventRecorder implements IntegrationEventRecorder {

        private final PostgresIntegrationEventRecorder delegate;
        private final AtomicBoolean failOutcomeEvents = new AtomicBoolean();

        FailingIntegrationEventRecorder(PostgresIntegrationEventRecorder delegate) {
            this.delegate = delegate;
        }

        @Override
        public void record(IntegrationEvent event, String routingKey) {
            if (failOutcomeEvents.get() && !(event instanceof EnrollmentCreatedEventV1)) {
                throw new DataIntegrityViolationException("Forced payment outcome outbox failure");
            }
            delegate.record(event, routingKey);
        }

        void failOutcomeEvents() {
            failOutcomeEvents.set(true);
        }

        void reset() {
            failOutcomeEvents.set(false);
        }
    }
}
