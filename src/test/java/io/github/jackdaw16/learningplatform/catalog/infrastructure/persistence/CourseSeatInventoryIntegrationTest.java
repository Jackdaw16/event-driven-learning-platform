package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseSeatInventory;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import io.github.jackdaw16.learningplatform.shared.Money;
import java.math.BigDecimal;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class CourseSeatInventoryIntegrationTest {

    private static final int CONCURRENT_WORKERS = 8;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InstructorRepository instructorRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSeatInventory courseSeatInventory;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
        jdbcTemplate.execute("TRUNCATE TABLE payments, enrollments, students, courses, categories, instructors");
        categoryId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        categoryRepository.save(new Category(categoryId, "Inventory", null));
        instructorRepository.save(new Instructor(instructorId, "Ada Lovelace", "ada.inventory@example.com", null));
    }

    @Test
    void reservesOneSeatForAnAvailablePublishedCourse() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0);

        assertTrue(inTransaction(() -> courseSeatInventory.reserve(course.id())));
        assertEquals(1, occupiedSeats(course.id()));
    }

    @Test
    void doesNotReserveAFullPublishedCourse() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1, 1);

        assertFalse(inTransaction(() -> courseSeatInventory.reserve(course.id())));
        assertEquals(1, occupiedSeats(course.id()));
    }

    @Test
    void doesNotReserveDraftArchivedOrMissingCourses() {
        Course draft = saveCourse(CourseStatus.DRAFT, 2, 0);
        Course archived = saveCourse(CourseStatus.ARCHIVED, 2, 0);

        assertFalse(inTransaction(() -> courseSeatInventory.reserve(draft.id())));
        assertFalse(inTransaction(() -> courseSeatInventory.reserve(archived.id())));
        assertFalse(inTransaction(() -> courseSeatInventory.reserve(UUID.randomUUID())));
        assertEquals(0, occupiedSeats(draft.id()));
        assertEquals(0, occupiedSeats(archived.id()));
    }

    @Test
    void releasesASeatFromPublishedAndArchivedCourses() {
        Course published = saveCourse(CourseStatus.PUBLISHED, 2, 1);
        Course archived = saveCourse(CourseStatus.ARCHIVED, 2, 1);

        assertTrue(inTransaction(() -> courseSeatInventory.release(published.id())));
        assertTrue(inTransaction(() -> courseSeatInventory.release(archived.id())));
        assertEquals(0, occupiedSeats(published.id()));
        assertEquals(0, occupiedSeats(archived.id()));
    }

    @Test
    void doesNotReleaseBelowZeroOrForAMissingCourse() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0);

        assertFalse(inTransaction(() -> courseSeatInventory.release(course.id())));
        assertFalse(inTransaction(() -> courseSeatInventory.release(UUID.randomUUID())));
        assertEquals(0, occupiedSeats(course.id()));
    }

    @Test
    void ordinaryCourseSaveDoesNotOverwriteAnInventorySeatReservation() {
        Course course = saveCourse(CourseStatus.PUBLISHED, 2, 0);
        Course staleCourse = courseRepository.findById(course.id()).orElseThrow();

        assertTrue(inTransaction(() -> courseSeatInventory.reserve(course.id())));
        staleCourse.revise(
                "Revised inventory course",
                staleCourse.description(),
                staleCourse.estimatedDurationHours(),
                staleCourse.level(),
                staleCourse.price(),
                staleCourse.maximumSeats(),
                staleCourse.categoryId(),
                staleCourse.instructorId()
        );
        courseRepository.save(staleCourse);

        Course reloaded = courseRepository.findById(course.id()).orElseThrow();
        assertEquals("Revised inventory course", reloaded.title());
        assertEquals(1, reloaded.occupiedSeats());
    }

    @Test
    void reservesOnlyOneOfTheLastSeatsAcrossConcurrentTransactions() throws Exception {
        Course course = saveCourse(CourseStatus.PUBLISHED, 1, 0);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WORKERS);
        List<Future<Boolean>> attempts = new ArrayList<>();

        try {
            for (int worker = 0; worker < CONCURRENT_WORKERS; worker++) {
                attempts.add(executor.submit(() -> transactionTemplate.execute(status -> {
                    ready.countDown();
                    await(start);
                    return courseSeatInventory.reserve(course.id());
                })));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int reservations = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    reservations++;
                }
            }

            assertEquals(1, reservations);
            assertEquals(1, occupiedSeats(course.id()));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Course saveCourse(CourseStatus status, int maximumSeats, int occupiedSeats) {
        Course course = new Course(
                UUID.randomUUID(),
                "Inventory course " + UUID.randomUUID(),
                null,
                8,
                CourseLevel.BEGINNER,
                new Money(new BigDecimal("19.99"), Currency.getInstance("USD")),
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

    private int occupiedSeats(UUID courseId) {
        return jdbcTemplate.queryForObject(
                "SELECT occupied_seats FROM courses WHERE id = ?",
                Integer.class,
                courseId
        );
    }

    private boolean inTransaction(InventoryOperation operation) {
        return transactionTemplate.execute(status -> operation.execute());
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

    @FunctionalInterface
    private interface InventoryOperation {

        boolean execute();
    }
}
