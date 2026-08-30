package io.github.jackdaw16.learningplatform.enrollment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseNotEnrollableException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseSeatUnavailableException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.EnrollmentAlreadyExistsException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.IdempotencyConflictException;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCreatedEventV1;
import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.IntegrationEvent;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence.PostgresIntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.payment.domain.PaymentStatus;
import io.github.jackdaw16.learningplatform.shared.Money;
import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import io.github.jackdaw16.learningplatform.student.domain.Student;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Testcontainers
@Import(EnrollmentCreationIntegrationTest.FailingIntegrationEventRecorderConfiguration.class)
class EnrollmentCreationIntegrationTest {

    private static final int CONCURRENT_WORKERS = 8;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EnrollmentCreationService enrollmentCreationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostgresIntegrationEventRecorder postgresIntegrationEventRecorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private FailingIntegrationEventRecorder failingIntegrationEventRecorder;

    private UUID categoryId;
    private UUID instructorId;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUpPrerequisites() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events, payments, enrollments, students, courses, categories, instructors");
        categoryId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        categoryRepository.save(new Category(categoryId, "Enrollment", null));
        instructorRepository.save(new Instructor(instructorId, "Ada Lovelace", "ada.enrollment@example.com", null));
    }

    @Test
    void rejectsInvalidCommandsAndMissingStudentOrCourse() {
        assertThrows(NullPointerException.class, () -> new CreateEnrollmentCommand(null, UUID.randomUUID(), "key"));
        assertThrows(NullPointerException.class, () -> new CreateEnrollmentCommand(UUID.randomUUID(), null, "key"));
        assertThrows(IllegalArgumentException.class, () -> new CreateEnrollmentCommand(UUID.randomUUID(), UUID.randomUUID(), " "));
        assertThrows(IllegalArgumentException.class, () -> new CreateEnrollmentCommand(
                UUID.randomUUID(), UUID.randomUUID(), "x".repeat(256)
        ));

        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0, new BigDecimal("19.99"));
        assertThrows(ResourceNotFoundException.class, () -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(UUID.randomUUID(), course.id(), "missing-student")
        ));

        Student student = saveStudent();
        assertThrows(ResourceNotFoundException.class, () -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), UUID.randomUUID(), "missing-course")
        ));
    }

    @Test
    void rejectsDraftArchivedAndFullCourses() {
        Student student = saveStudent();
        Course draft = saveCourse(CourseStatus.DRAFT, 2, 0, new BigDecimal("19.99"));
        Course archived = saveCourse(CourseStatus.ARCHIVED, 2, 0, new BigDecimal("19.99"));
        Course full = saveCourse(CourseStatus.PUBLISHED, 1, 1, new BigDecimal("19.99"));

        assertThrows(CourseNotEnrollableException.class, () -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), draft.id(), "draft")
        ));
        assertThrows(CourseNotEnrollableException.class, () -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), archived.id(), "archived")
        ));
        assertThrows(CourseSeatUnavailableException.class, () -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), full.id(), "full")
        ));
        assertEquals(1, occupiedSeats(full.id()));
        assertEquals(0, enrollmentCount());
        assertEquals(0, paymentCount());
        assertEquals(0, outboxEventCount());
    }

    @Test
    void createsPendingEnrollmentAndPaymentWithCoursePriceSnapshot() {
        Student student = saveStudent();
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0, new BigDecimal("49.9900"));

        CreateEnrollmentResult result = enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), course.id(), "successful-enrollment")
        );

        assertFalse(result.replayed());
        assertEquals(EnrollmentStatus.PENDING_PAYMENT, result.enrollment().status());
        assertEquals(PaymentStatus.PENDING, result.payment().status());
        assertEquals(result.enrollment().id(), result.payment().enrollmentId());
        assertEquals(new BigDecimal("49.9900"), result.payment().amount().amount());
        assertEquals(Currency.getInstance("USD"), result.payment().amount().currency());
        assertEquals(1, occupiedSeats(course.id()));
        assertEquals(1, enrollmentCount());
        assertEquals(1, paymentCount());
    }

    @Test
    void recordsPendingOutboxEventWithExactEnrollmentCreatedMetadataAndPayload() throws Exception {
        Student student = saveStudent();
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0, new BigDecimal("49.9900"));

        CreateEnrollmentResult result = enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), course.id(), "outbox-event")
        );

        OutboxEventRow row = outboxEvent();
        EnrollmentCreatedEventV1 payload = objectMapper.readValue(row.payload(), EnrollmentCreatedEventV1.class);

        assertEquals(1, outboxEventCount());
        assertEquals(payload.metadata().eventId(), row.eventId());
        assertEquals(EnrollmentCreatedEventV1.EVENT_TYPE, row.eventType());
        assertEquals(EnrollmentCreatedEventV1.VERSION, row.eventVersion());
        assertEquals(row.eventType(), payload.metadata().eventType());
        assertEquals(row.eventVersion(), payload.metadata().version());
        assertEquals(payload.metadata().occurredAt(), row.occurredAt());
        assertEquals(RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY, row.routingKey());
        assertEquals("PENDING", row.status());
        assertEquals(row.occurredAt(), row.createdAt());
        assertNull(row.publishedAt());
        assertEquals(0, row.attemptCount());
        assertEquals(result.enrollment().id(), payload.enrollmentId());
        assertEquals(result.payment().id(), payload.paymentId());
        assertEquals(result.payment().amount().amount(), payload.amount());
        assertEquals(result.payment().amount().currency().getCurrencyCode(), payload.currency());
    }

    @Test
    void rejectsDuplicateOutboxEventIdsAtTheDatabaseBoundary() {
        EnrollmentCreatedEventV1 event = new EnrollmentCreatedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        EnrollmentCreatedEventV1.EVENT_TYPE,
                        EnrollmentCreatedEventV1.VERSION,
                        Instant.parse("2026-08-30T12:00:00Z")
                ),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("19.99"),
                "USD"
        );

        transactionTemplate.executeWithoutResult(status -> postgresIntegrationEventRecorder.record(
                event,
                RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY
        ));

        assertThrows(DataIntegrityViolationException.class, () -> transactionTemplate.executeWithoutResult(
                status -> postgresIntegrationEventRecorder.record(event, RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY)
        ));
        assertEquals(1, outboxEventCount());
    }

    @Test
    void rejectsOutboxEventRecordingWithoutAnExistingTransaction() {
        EnrollmentCreatedEventV1 event = new EnrollmentCreatedEventV1(
                new EventMetadataV1(
                        UUID.randomUUID(),
                        EnrollmentCreatedEventV1.EVENT_TYPE,
                        EnrollmentCreatedEventV1.VERSION,
                        Instant.parse("2026-08-30T12:00:00Z")
                ),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("19.99"),
                "USD"
        );

        assertThrows(IllegalTransactionStateException.class, () -> postgresIntegrationEventRecorder.record(
                event,
                RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY
        ));
        assertEquals(0, outboxEventCount());
    }

    @Test
    void rollsBackSeatReservationEnrollmentPaymentAndOutboxWhenEventRecordingFails() {
        Student student = saveStudent();
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0, new BigDecimal("19.99"));
        failingIntegrationEventRecorder.failNextRecording();

        assertThrows(DataIntegrityViolationException.class, () -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), course.id(), "outbox-failure")
        ));

        assertEquals(0, occupiedSeats(course.id()));
        assertEquals(0, enrollmentCount());
        assertEquals(0, paymentCount());
        assertEquals(0, outboxEventCount());
    }

    @Test
    void replaysSameKeyWithoutReservingAnotherSeatAndRejectsDifferentPayload() {
        Student student = saveStudent();
        Student otherStudent = saveStudent();
        Course course = saveCourse(CourseStatus.PUBLISHED, 3, 0, new BigDecimal("19.99"));
        CreateEnrollmentCommand command = new CreateEnrollmentCommand(student.id(), course.id(), "replay-key");

        CreateEnrollmentResult created = enrollmentCreationService.create(command);
        CreateEnrollmentResult replayed = enrollmentCreationService.create(command);

        assertFalse(created.replayed());
        assertTrue(replayed.replayed());
        assertEquals(created.enrollment().id(), replayed.enrollment().id());
        assertEquals(created.payment().id(), replayed.payment().id());
        assertEquals(1, occupiedSeats(course.id()));
        assertEquals(1, enrollmentCount());
        assertEquals(1, paymentCount());
        assertEquals(1, outboxEventCount());
        assertThrows(IdempotencyConflictException.class, () -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(otherStudent.id(), course.id(), "replay-key")
        ));
    }

    @Test
    void allowsNewEnrollmentAfterCancelledHistoricalEnrollment() {
        Student student = saveStudent();
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0, new BigDecimal("19.99"));
        Enrollment cancelled = new Enrollment(UUID.randomUUID(), student.id(), course.id(), Instant.parse("2026-08-30T12:00:00Z"));
        cancelled.cancel();
        enrollmentRepository.save(cancelled);

        CreateEnrollmentResult result = enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), course.id(), "after-cancelled")
        );

        assertEquals(EnrollmentStatus.PENDING_PAYMENT, result.enrollment().status());
        assertEquals(2, enrollmentCount());
        assertEquals(1, paymentCount());
        assertEquals(1, occupiedSeats(course.id()));
    }

    @Test
    void permitsOnlyOneOfEightDistinctStudentsToTakeTheLastSeat() throws Exception {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1, 0, new BigDecimal("19.99"));
        List<Student> students = new ArrayList<>();
        for (int worker = 0; worker < CONCURRENT_WORKERS; worker++) {
            students.add(saveStudent());
        }

        List<Attempt<CreateEnrollmentResult>> attempts = concurrently(CONCURRENT_WORKERS, worker -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(students.get(worker).id(), course.id(), "last-seat-" + worker)
        ));

        assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
        assertEquals(CONCURRENT_WORKERS - 1, attempts.stream().filter(attempt -> !attempt.succeeded()).count());
        attempts.stream().filter(attempt -> !attempt.succeeded())
                .forEach(attempt -> assertInstanceOf(CourseSeatUnavailableException.class, attempt.failure()));
        assertEquals(1, occupiedSeats(course.id()));
        assertEquals(1, enrollmentCount());
        assertEquals(1, paymentCount());
        assertEquals(1, outboxEventCount());
    }

    @Test
    void concurrentSameKeyRequestsReturnTheSameEnrollmentAndPayment() throws Exception {
        Student student = saveStudent();
        Course course = saveCourse(CourseStatus.PUBLISHED, CONCURRENT_WORKERS, 0, new BigDecimal("19.99"));
        CreateEnrollmentCommand command = new CreateEnrollmentCommand(student.id(), course.id(), "concurrent-same-key");

        List<Attempt<CreateEnrollmentResult>> attempts = concurrently(
                CONCURRENT_WORKERS,
                ignored -> enrollmentCreationService.create(command)
        );

        assertTrue(attempts.stream().allMatch(Attempt::succeeded));
        UUID enrollmentId = attempts.getFirst().result().enrollment().id();
        UUID paymentId = attempts.getFirst().result().payment().id();
        attempts.forEach(attempt -> {
            assertEquals(enrollmentId, attempt.result().enrollment().id());
            assertEquals(paymentId, attempt.result().payment().id());
        });
        assertTrue(attempts.stream().anyMatch(attempt -> !attempt.result().replayed()));
        assertEquals(1, occupiedSeats(course.id()));
        assertEquals(1, enrollmentCount());
        assertEquals(1, paymentCount());
        assertEquals(1, outboxEventCount());
    }

    @Test
    void concurrentSameKeyDifferentPayloadRollsBackThePaymentKeyLoserBeforeConflictRecovery() throws Exception {
        Student firstStudent = saveStudent();
        Student secondStudent = saveStudent();
        Course firstCourse = saveCourse(CourseStatus.PUBLISHED, 1, 0, new BigDecimal("19.99"));
        Course secondCourse = saveCourse(CourseStatus.PUBLISHED, 1, 0, new BigDecimal("19.99"));

        List<Attempt<CreateEnrollmentResult>> attempts = concurrently(2, worker -> enrollmentCreationService.create(
                worker == 0
                        ? new CreateEnrollmentCommand(firstStudent.id(), firstCourse.id(), "same-key-different-payload")
                        : new CreateEnrollmentCommand(secondStudent.id(), secondCourse.id(), "same-key-different-payload")
        ));

        assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
        Attempt<CreateEnrollmentResult> failedAttempt = attempts.stream().filter(attempt -> !attempt.succeeded()).findFirst().orElseThrow();
        assertInstanceOf(IdempotencyConflictException.class, failedAttempt.failure());
        assertEquals(1, occupiedSeats(firstCourse.id()) + occupiedSeats(secondCourse.id()));
        assertEquals(1, enrollmentCount());
        assertEquals(1, paymentCount());
        assertEquals(1, outboxEventCount());
    }

    @Test
    void concurrentDifferentKeysForTheSameStudentAndCourseRollBackTheLosingReservation() throws Exception {
        Student student = saveStudent();
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0, new BigDecimal("19.99"));

        List<Attempt<CreateEnrollmentResult>> attempts = concurrently(2, worker -> enrollmentCreationService.create(
                new CreateEnrollmentCommand(student.id(), course.id(), "different-key-" + worker)
        ));

        assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
        Attempt<CreateEnrollmentResult> failedAttempt = attempts.stream().filter(attempt -> !attempt.succeeded()).findFirst().orElseThrow();
        assertInstanceOf(EnrollmentAlreadyExistsException.class, failedAttempt.failure());
        assertEquals(1, occupiedSeats(course.id()));
        assertEquals(1, enrollmentCount());
        assertEquals(1, paymentCount());
        assertEquals(1, outboxEventCount());
    }

    private Student saveStudent() {
        Student student = new Student(
                UUID.randomUUID(),
                "Student",
                "Example",
                "student-" + UUID.randomUUID() + "@example.com",
                Instant.parse("2026-08-30T12:00:00Z")
        );
        studentRepository.save(student);
        return student;
    }

    private Course saveCourse(CourseStatus status, int maximumSeats, int occupiedSeats, BigDecimal price) {
        Course course = new Course(
                UUID.randomUUID(),
                "Enrollment course " + UUID.randomUUID(),
                null,
                8,
                CourseLevel.BEGINNER,
                new Money(price, Currency.getInstance("USD")),
                maximumSeats,
                categoryId,
                instructorId
        );
        if (status != CourseStatus.DRAFT) {
            course.publish();
            for (int seat = 0; seat < occupiedSeats; seat++) {
                course.reserveSeat();
            }
            if (status == CourseStatus.ARCHIVED) {
                course.archive();
            }
        }
        courseRepository.save(course);
        return course;
    }

    private List<Attempt<CreateEnrollmentResult>> concurrently(
            int workerCount,
            IntFunction<CreateEnrollmentResult> operation
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<Attempt<CreateEnrollmentResult>>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < workerCount; worker++) {
                int workerIndex = worker;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        return Attempt.success(operation.apply(workerIndex));
                    } catch (Throwable failure) {
                        return Attempt.failure(failure);
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Attempt<CreateEnrollmentResult>> attempts = new ArrayList<>();
            for (Future<Attempt<CreateEnrollmentResult>> future : futures) {
                attempts.add(future.get(20, TimeUnit.SECONDS));
            }
            return attempts;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent workers");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent workers", exception);
        }
    }

    private int occupiedSeats(UUID courseId) {
        return jdbcTemplate.queryForObject(
                "SELECT occupied_seats FROM courses WHERE id = ?",
                Integer.class,
                courseId
        );
    }

    private int enrollmentCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM enrollments", Integer.class);
    }

    private int paymentCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Integer.class);
    }

    private int outboxEventCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
    }

    private OutboxEventRow outboxEvent() {
        return jdbcTemplate.queryForObject(
                "SELECT event_id, event_type, event_version, occurred_at, routing_key, payload::text AS payload, "
                        + "status, created_at, published_at, attempt_count FROM outbox_events",
                (resultSet, rowNum) -> new OutboxEventRow(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getInt("event_version"),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getString("routing_key"),
                        resultSet.getString("payload"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("published_at") == null
                                ? null
                                : resultSet.getTimestamp("published_at").toInstant(),
                        resultSet.getInt("attempt_count")
                )
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingIntegrationEventRecorderConfiguration {

        @Bean
        @Primary
        FailingIntegrationEventRecorder failingIntegrationEventRecorder(
                PostgresIntegrationEventRecorder delegate
        ) {
            return new FailingIntegrationEventRecorder(delegate);
        }
    }

    static final class FailingIntegrationEventRecorder implements IntegrationEventRecorder {

        private final PostgresIntegrationEventRecorder delegate;
        private final AtomicBoolean failNextRecording = new AtomicBoolean();

        FailingIntegrationEventRecorder(PostgresIntegrationEventRecorder delegate) {
            this.delegate = delegate;
        }

        void failNextRecording() {
            failNextRecording.set(true);
        }

        @Override
        public void record(IntegrationEvent event, String routingKey) {
            if (failNextRecording.compareAndSet(true, false)) {
                throw new DataIntegrityViolationException("Forced outbox persistence failure");
            }
            delegate.record(event, routingKey);
        }
    }

    private record OutboxEventRow(
            UUID eventId,
            String eventType,
            int eventVersion,
            Instant occurredAt,
            String routingKey,
            String payload,
            String status,
            Instant createdAt,
            Instant publishedAt,
            int attemptCount
    ) {
    }

    private record Attempt<T>(T result, Throwable failure) {

        static <T> Attempt<T> success(T result) {
            return new Attempt<>(result, null);
        }

        static <T> Attempt<T> failure(Throwable failure) {
            assertNotNull(failure);
            return new Attempt<>(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
    }
}
