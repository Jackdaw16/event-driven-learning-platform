package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentRepository;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import io.github.jackdaw16.learningplatform.shared.Money;
import io.github.jackdaw16.learningplatform.student.application.port.StudentRepository;
import io.github.jackdaw16.learningplatform.student.domain.Student;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class EnrollmentPaymentPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        jdbcTemplate.execute("TRUNCATE TABLE certificates, payments, enrollments, students, courses, categories, instructors");
        categoryId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        categoryRepository.save(new Category(categoryId, "Persistence", null));
        instructorRepository.save(new Instructor(instructorId, "Ada Lovelace", "ada.lovelace@example.com", null));
    }

    @Test
    void roundTripsEnrollmentLifecycleStatesAndReturnsEmptyForMissingId() {
        Enrollment pending = newEnrollment();
        Enrollment active = newEnrollment();
        active.activate();
        active.updateProgress(37, null);
        Enrollment completed = newEnrollment();
        completed.activate();
        completed.updateProgress(100, Instant.parse("2026-08-30T12:34:56.123456Z"));
        Enrollment cancelled = newEnrollment();
        cancelled.cancel();

        enrollmentRepository.save(pending);
        enrollmentRepository.save(active);
        enrollmentRepository.save(completed);
        enrollmentRepository.save(cancelled);

        assertEnrollmentEquals(pending, enrollmentRepository.findById(pending.id()).orElseThrow());
        assertEnrollmentEquals(active, enrollmentRepository.findById(active.id()).orElseThrow());
        assertEnrollmentEquals(completed, enrollmentRepository.findById(completed.id()).orElseThrow());
        assertEnrollmentEquals(cancelled, enrollmentRepository.findById(cancelled.id()).orElseThrow());
        assertTrue(enrollmentRepository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void roundTripsPaymentStatesAndFindsByEnrollmentAndIdempotencyKey() {
        Payment pending = newPayment(newEnrollment(), new BigDecimal("12.3400"), "payment-pending");
        Payment confirmed = newPayment(newEnrollment(), new BigDecimal("0.010"), "payment-confirmed");
        confirmed.confirm();
        Payment failed = newPayment(newEnrollment(), new BigDecimal("99.99000"), "payment-failed");
        failed.fail();

        paymentRepository.save(pending);
        paymentRepository.save(confirmed);
        paymentRepository.save(failed);

        assertPaymentEquals(pending, paymentRepository.findById(pending.id()).orElseThrow());
        assertPaymentEquals(confirmed, paymentRepository.findByEnrollmentId(confirmed.enrollmentId()).orElseThrow());
        assertPaymentEquals(failed, paymentRepository.findByIdempotencyKey(failed.idempotencyKey()).orElseThrow());
        assertTrue(paymentRepository.findById(UUID.randomUUID()).isEmpty());
        assertTrue(paymentRepository.findByEnrollmentId(UUID.randomUUID()).isEmpty());
        assertTrue(paymentRepository.findByIdempotencyKey("missing-key").isEmpty());
    }

    private Enrollment newEnrollment() {
        Student student = new Student(
                UUID.randomUUID(),
                "Student",
                "Example",
                "student-" + UUID.randomUUID() + "@example.com",
                Instant.parse("2026-08-30T12:00:00.123456Z")
        );
        Course course = new Course(
                UUID.randomUUID(),
                "Persistence Course " + UUID.randomUUID(),
                null,
                8,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("19.9900"), Currency.getInstance("USD")),
                10,
                categoryId,
                instructorId
        );
        studentRepository.save(student);
        courseRepository.save(course);
        return new Enrollment(
                UUID.randomUUID(),
                student.id(),
                course.id(),
                Instant.parse("2026-08-30T12:01:02.654321Z")
        );
    }

    private Payment newPayment(Enrollment enrollment, BigDecimal amount, String idempotencyKey) {
        enrollmentRepository.save(enrollment);
        return new Payment(
                UUID.randomUUID(),
                enrollment.id(),
                new Money(amount, Currency.getInstance("USD")),
                idempotencyKey,
                Instant.parse("2026-08-30T12:03:04.123456Z")
        );
    }

    private void assertEnrollmentEquals(Enrollment expected, Enrollment actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.studentId(), actual.studentId());
        assertEquals(expected.courseId(), actual.courseId());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.progress(), actual.progress());
        assertEquals(expected.enrolledAt(), actual.enrolledAt());
        assertEquals(expected.completedAt(), actual.completedAt());
    }

    private void assertPaymentEquals(Payment expected, Payment actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.enrollmentId(), actual.enrollmentId());
        assertEquals(expected.amount().amount(), actual.amount().amount());
        assertEquals(expected.amount().currency(), actual.amount().currency());
        assertEquals(expected.idempotencyKey(), actual.idempotencyKey());
        assertEquals(expected.createdAt(), actual.createdAt());
        assertEquals(expected.status(), actual.status());
    }
}
