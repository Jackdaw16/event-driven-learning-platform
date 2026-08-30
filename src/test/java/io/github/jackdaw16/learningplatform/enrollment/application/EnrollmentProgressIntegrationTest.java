package io.github.jackdaw16.learningplatform.enrollment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCompletedEventV1;
import io.github.jackdaw16.learningplatform.messaging.IntegrationEvent;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence.PostgresIntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.shared.Money;
import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import io.github.jackdaw16.learningplatform.student.domain.Student;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "messaging.outbox.poll-interval=1h")
@Testcontainers
@Import(EnrollmentProgressIntegrationTest.ProgressTestConfiguration.class)
class EnrollmentProgressIntegrationTest {

    private static final Instant COMPLETED_AT = Instant.parse("2026-08-30T12:00:00.123456Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EnrollmentProgressService enrollmentProgressService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FailingIntegrationEventRecorder failingIntegrationEventRecorder;

    private UUID categoryId;
    private UUID instructorId;

    @org.springframework.test.context.DynamicPropertySource
    static void configureDatasource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events, certificates, payments, enrollments, students, courses, categories, instructors");
        categoryId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        categoryRepository.save(new Category(categoryId, "Progress", null));
        instructorRepository.save(new Instructor(instructorId, "Ada Lovelace", "ada.progress@example.com", null));
    }

    @Test
    void updatesActiveProgressBelowCompletionWithoutRecordingAnEvent() {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.ACTIVE, 10);

        Enrollment updated = enrollmentProgressService.updateProgress(enrollment.id(), 75);

        assertEquals(EnrollmentStatus.ACTIVE, updated.status());
        assertEquals(75, updated.progress());
        assertNull(updated.completedAt());
        Enrollment persisted = enrollmentRepository.findById(enrollment.id()).orElseThrow();
        assertEquals(EnrollmentStatus.ACTIVE, persisted.status());
        assertEquals(75, persisted.progress());
        assertNull(persisted.completedAt());
        assertEquals(0, completionOutboxEventCount());
    }

    @Test
    void completesEnrollmentAndRecordsExactlyOneMatchingOutboxEvent() throws Exception {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.ACTIVE, 75);

        Enrollment completed = enrollmentProgressService.updateProgress(enrollment.id(), 100);

        assertEquals(EnrollmentStatus.COMPLETED, completed.status());
        assertEquals(100, completed.progress());
        assertEquals(COMPLETED_AT, completed.completedAt());
        Enrollment persisted = enrollmentRepository.findById(enrollment.id()).orElseThrow();
        assertEquals(EnrollmentStatus.COMPLETED, persisted.status());
        assertEquals(100, persisted.progress());
        assertEquals(COMPLETED_AT, persisted.completedAt());

        OutboxEventRow row = completionOutboxEvent();
        EnrollmentCompletedEventV1 event = objectMapper.readValue(row.payload(), EnrollmentCompletedEventV1.class);
        assertEquals(1, completionOutboxEventCount());
        assertNotEquals(enrollment.id(), row.eventId());
        assertEquals(row.eventId(), event.metadata().eventId());
        assertEquals(EnrollmentCompletedEventV1.EVENT_TYPE, row.eventType());
        assertEquals(EnrollmentCompletedEventV1.VERSION, row.eventVersion());
        assertEquals(RabbitTopology.ENROLLMENT_COMPLETED_ROUTING_KEY, row.routingKey());
        assertEquals(COMPLETED_AT, row.occurredAt());
        assertEquals(persisted.completedAt(), event.metadata().occurredAt());
        assertEquals(enrollment.id(), event.enrollmentId());
        assertEquals(enrollment.studentId(), event.studentId());
        assertEquals(enrollment.courseId(), event.courseId());
    }

    @Test
    void treatsRepeatedCompletionAsAnIdempotentNoOp() {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.ACTIVE, 80);
        Enrollment completed = enrollmentProgressService.updateProgress(enrollment.id(), 100);

        Enrollment repeated = enrollmentProgressService.updateProgress(enrollment.id(), 100);

        assertEquals(completed.completedAt(), repeated.completedAt());
        Enrollment persisted = enrollmentRepository.findById(enrollment.id()).orElseThrow();
        assertEquals(EnrollmentStatus.COMPLETED, persisted.status());
        assertEquals(100, persisted.progress());
        assertEquals(completed.completedAt(), persisted.completedAt());
        assertEquals(1, completionOutboxEventCount());
    }

    @Test
    void rejectsPendingPaymentAndCancelledEnrollmentProgressUpdates() {
        Enrollment pendingPayment = saveEnrollment(EnrollmentStatus.PENDING_PAYMENT, 0);
        Enrollment cancelled = saveEnrollment(EnrollmentStatus.CANCELLED, 20);

        assertThrows(IllegalStateException.class, () -> enrollmentProgressService.updateProgress(pendingPayment.id(), 10));
        assertThrows(IllegalStateException.class, () -> enrollmentProgressService.updateProgress(cancelled.id(), 10));
        assertEquals(0, enrollmentRepository.findById(pendingPayment.id()).orElseThrow().progress());
        assertEquals(20, enrollmentRepository.findById(cancelled.id()).orElseThrow().progress());
        assertEquals(0, completionOutboxEventCount());
    }

    @Test
    void reportsMissingEnrollmentAsAnApplicationNotFoundError() {
        assertThrows(ResourceNotFoundException.class, () -> enrollmentProgressService.updateProgress(UUID.randomUUID(), 75));
    }

    @Test
    void rollsBackCompletionWhenOutboxRecordingFails() {
        Enrollment enrollment = saveEnrollment(EnrollmentStatus.ACTIVE, 55);
        failingIntegrationEventRecorder.failNextRecording();

        assertThrows(DataIntegrityViolationException.class, () -> enrollmentProgressService.updateProgress(enrollment.id(), 100));

        Enrollment persisted = enrollmentRepository.findById(enrollment.id()).orElseThrow();
        assertEquals(EnrollmentStatus.ACTIVE, persisted.status());
        assertEquals(55, persisted.progress());
        assertNull(persisted.completedAt());
        assertEquals(0, completionOutboxEventCount());
    }

    private Enrollment saveEnrollment(EnrollmentStatus status, int progress) {
        Student student = new Student(
                UUID.randomUUID(),
                "Student",
                "Example",
                "student-" + UUID.randomUUID() + "@example.com",
                Instant.parse("2026-08-30T12:00:00Z")
        );
        studentRepository.save(student);
        Course course = new Course(
                UUID.randomUUID(),
                "Progress course " + UUID.randomUUID(),
                null,
                8,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("19.99"), Currency.getInstance("USD")),
                10,
                categoryId,
                instructorId
        );
        courseRepository.save(course);
        Enrollment enrollment = new Enrollment(
                UUID.randomUUID(),
                student.id(),
                course.id(),
                Instant.parse("2026-08-30T12:00:00Z")
        );
        if (status != EnrollmentStatus.PENDING_PAYMENT) {
            enrollment.activate();
            if (progress > 0) {
                enrollment.updateProgress(progress, null);
            }
            if (status == EnrollmentStatus.CANCELLED) {
                enrollment.cancel();
            }
        }
        enrollmentRepository.save(enrollment);
        return enrollment;
    }

    private int completionOutboxEventCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = ?",
                Integer.class,
                EnrollmentCompletedEventV1.EVENT_TYPE
        );
    }

    private OutboxEventRow completionOutboxEvent() {
        return jdbcTemplate.queryForObject(
                "SELECT event_id, event_type, event_version, occurred_at, routing_key, payload::text AS payload "
                        + "FROM outbox_events WHERE event_type = ?",
                (resultSet, rowNum) -> new OutboxEventRow(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getInt("event_version"),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getString("routing_key"),
                        resultSet.getString("payload")
                ),
                EnrollmentCompletedEventV1.EVENT_TYPE
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProgressTestConfiguration {

        @Bean
        @Primary
        Clock progressTestClock() {
            return Clock.fixed(COMPLETED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        FailingIntegrationEventRecorder failingIntegrationEventRecorder(PostgresIntegrationEventRecorder delegate) {
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
            String payload
    ) {
    }
}
