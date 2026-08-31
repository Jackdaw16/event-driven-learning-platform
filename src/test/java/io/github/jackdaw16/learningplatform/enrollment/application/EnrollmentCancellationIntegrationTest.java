package io.github.jackdaw16.learningplatform.enrollment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import io.github.jackdaw16.learningplatform.enrollment.application.exception.CourseSeatReleaseFailedException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.EnrollmentCancellationNotAllowedException;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EnrollmentCancellationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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
    private EnrollmentRepository enrollmentRepository;

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
        categoryRepository.save(new Category(categoryId, "Cancellation", null));
        instructorRepository.save(new Instructor(instructorId, "Ada Lovelace", "ada.cancellation@example.com", null));
    }

    @Test
    void cancelsPendingPaymentEnrollmentAndReleasesOneSeat() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1);
        Enrollment enrollment = saveEnrollment(course, EnrollmentStatus.PENDING_PAYMENT);

        Enrollment cancelled = enrollmentCancellationService.cancel(enrollment.id());

        assertEquals(EnrollmentStatus.CANCELLED, cancelled.status());
        assertEquals(EnrollmentStatus.CANCELLED, enrollmentStatus(enrollment.id()));
        assertEquals(0, occupiedSeats(course.id()));
    }

    @Test
    void cancelsActiveEnrollmentAndReleasesOneSeat() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1);
        Enrollment enrollment = saveEnrollment(course, EnrollmentStatus.ACTIVE);

        Enrollment cancelled = enrollmentCancellationService.cancel(enrollment.id());

        assertEquals(EnrollmentStatus.CANCELLED, cancelled.status());
        assertEquals(EnrollmentStatus.CANCELLED, enrollmentStatus(enrollment.id()));
        assertEquals(0, occupiedSeats(course.id()));
    }

    @Test
    void repeatedCancellationDoesNotReleaseTheSeatTwice() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1);
        Enrollment enrollment = saveEnrollment(course, EnrollmentStatus.PENDING_PAYMENT);

        enrollmentCancellationService.cancel(enrollment.id());
        Enrollment repeated = enrollmentCancellationService.cancel(enrollment.id());

        assertEquals(EnrollmentStatus.CANCELLED, repeated.status());
        assertEquals(0, occupiedSeats(course.id()));
    }

    @Test
    void cancellationReleasesSeatForArchivedCourse() {
        Course course = saveCourse(CourseStatus.ARCHIVED, 1);
        Enrollment enrollment = saveEnrollment(course, EnrollmentStatus.PENDING_PAYMENT);

        enrollmentCancellationService.cancel(enrollment.id());

        assertEquals(EnrollmentStatus.CANCELLED, enrollmentStatus(enrollment.id()));
        assertEquals(0, occupiedSeats(course.id()));
    }

    @Test
    void rejectsCompletedCancellationWithoutChangingOccupancy() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1);
        Enrollment enrollment = saveEnrollment(course, EnrollmentStatus.COMPLETED);

        assertThrows(EnrollmentCancellationNotAllowedException.class, () -> enrollmentCancellationService.cancel(enrollment.id()));

        assertEquals(EnrollmentStatus.COMPLETED, enrollmentStatus(enrollment.id()));
        assertEquals(1, occupiedSeats(course.id()));
    }

    @Test
    void rejectsMissingEnrollment() {
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> enrollmentCancellationService.cancel(UUID.randomUUID())
        );

        assertTrue(exception.getMessage().startsWith("Enrollment with id "));
    }

    @Test
    void rollsBackCancellationWhenSeatReleaseFails() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 0);
        Enrollment enrollment = saveEnrollment(course, EnrollmentStatus.PENDING_PAYMENT);

        assertThrows(CourseSeatReleaseFailedException.class, () -> enrollmentCancellationService.cancel(enrollment.id()));

        assertEquals(EnrollmentStatus.PENDING_PAYMENT, enrollmentStatus(enrollment.id()));
        assertEquals(0, occupiedSeats(course.id()));
    }

    @Test
    void concurrentCancellationsReleaseExactlyOneSeatAndBothReturnCancelled() throws Exception {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1);
        Enrollment enrollment = saveEnrollment(course, EnrollmentStatus.PENDING_PAYMENT);

        List<Attempt<Enrollment>> attempts = concurrently(() -> enrollmentCancellationService.cancel(enrollment.id()));

        assertEquals(2, attempts.size());
        assertTrue(attempts.stream().allMatch(Attempt::succeeded));
        attempts.forEach(attempt -> assertEquals(EnrollmentStatus.CANCELLED, attempt.result().status()));
        assertEquals(EnrollmentStatus.CANCELLED, enrollmentStatus(enrollment.id()));
        assertEquals(0, occupiedSeats(course.id()));
    }

    private Course saveCourse(CourseStatus status, int occupiedSeats) {
        Course course = new Course(
                UUID.randomUUID(),
                "Cancellation course " + UUID.randomUUID(),
                null,
                8,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("19.99"), Currency.getInstance("USD")),
                2,
                categoryId,
                instructorId
        );
        course.publish();
        for (int seat = 0; seat < occupiedSeats; seat++) {
            course.reserveSeat();
        }
        if (status == CourseStatus.ARCHIVED) {
            course.archive();
        }
        courseRepository.save(course);
        return course;
    }

    private Enrollment saveEnrollment(Course course, EnrollmentStatus status) {
        Student student = new Student(
                UUID.randomUUID(),
                "Student",
                "Example",
                "student-" + UUID.randomUUID() + "@example.com",
                Instant.parse("2026-08-30T12:00:00Z")
        );
        studentRepository.save(student);
        Enrollment enrollment = new Enrollment(
                UUID.randomUUID(), student.id(), course.id(), Instant.parse("2026-08-30T12:00:00Z")
        );
        if (status == EnrollmentStatus.ACTIVE || status == EnrollmentStatus.COMPLETED) {
            enrollment.activate();
        }
        if (status == EnrollmentStatus.COMPLETED) {
            enrollment.updateProgress(100, Instant.parse("2026-08-30T12:30:00Z"));
        }
        enrollmentRepository.save(enrollment);
        return enrollment;
    }

    private List<Attempt<Enrollment>> concurrently(CancellationOperation operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Attempt<Enrollment>>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < 2; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        return Attempt.success(operation.execute());
                    } catch (Throwable failure) {
                        return Attempt.failure(failure);
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Attempt<Enrollment>> attempts = new ArrayList<>();
            for (Future<Attempt<Enrollment>> future : futures) {
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

    private EnrollmentStatus enrollmentStatus(UUID enrollmentId) {
        return EnrollmentStatus.valueOf(jdbcTemplate.queryForObject(
                "SELECT status FROM enrollments WHERE id = ?",
                String.class,
                enrollmentId
        ));
    }

    private int occupiedSeats(UUID courseId) {
        return jdbcTemplate.queryForObject(
                "SELECT occupied_seats FROM courses WHERE id = ?",
                Integer.class,
                courseId
        );
    }

    @FunctionalInterface
    private interface CancellationOperation {

        Enrollment execute();
    }

    private record Attempt<T>(T result, Throwable failure) {

        static <T> Attempt<T> success(T result) {
            return new Attempt<>(result, null);
        }

        static <T> Attempt<T> failure(Throwable failure) {
            return new Attempt<>(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
    }
}
