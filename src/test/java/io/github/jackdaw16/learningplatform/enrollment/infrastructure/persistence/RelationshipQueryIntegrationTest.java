package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentRelationshipQueryService;
import io.github.jackdaw16.learningplatform.enrollment.application.RelationshipPageQuery;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

@SpringBootTest(properties = {
        "messaging.outbox.poll-interval=1h",
        "management.health.rabbit.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Testcontainers
class RelationshipQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EnrollmentRelationshipQueryService relationshipQueryService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UUID courseId;
    private UUID studentId;
    private Statistics statistics;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_users, certificates, payments, enrollments, students, courses, categories, instructors");
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        seedCategory(categoryId);
        seedInstructor(instructorId);
        seedStudent(studentId, "Primary");
        seedCourse(courseId, categoryId, instructorId, "Primary course");

        for (int index = 0; index < 3; index++) {
            UUID courseStudentId = UUID.randomUUID();
            seedStudent(courseStudentId, "Course student " + index);
            seedEnrollment(UUID.randomUUID(), courseStudentId, courseId, index);

            UUID studentCourseId = UUID.randomUUID();
            seedCourse(studentCourseId, categoryId, instructorId, "Student course " + index);
            seedEnrollment(UUID.randomUUID(), studentId, studentCourseId, index);
        }
    }

    @Test
    void courseStudentsProjectionUsesTwoStatementsRegardlessOfPageSize() {
        long oneRowPageStatements = statementCount(() ->
                relationshipQueryService.findStudentsByCourseId(courseId, new RelationshipPageQuery(0, 1))
        );
        long twoRowPageStatements = statementCount(() ->
                relationshipQueryService.findStudentsByCourseId(courseId, new RelationshipPageQuery(0, 2))
        );

        assertEquals(2, oneRowPageStatements);
        assertEquals(oneRowPageStatements, twoRowPageStatements);
    }

    @Test
    void studentCoursesProjectionUsesTwoStatementsRegardlessOfPageSize() {
        long oneRowPageStatements = statementCount(() ->
                relationshipQueryService.findCoursesByStudentId(studentId, new RelationshipPageQuery(0, 1))
        );
        long twoRowPageStatements = statementCount(() ->
                relationshipQueryService.findCoursesByStudentId(studentId, new RelationshipPageQuery(0, 2))
        );

        assertEquals(2, oneRowPageStatements);
        assertEquals(oneRowPageStatements, twoRowPageStatements);
    }

    private long statementCount(Runnable query) {
        statistics.clear();
        query.run();
        return statistics.getPrepareStatementCount();
    }

    private void seedCategory(UUID categoryId) {
        jdbcTemplate.update("INSERT INTO categories (id, name, description, status) VALUES (?, ?, NULL, 'ACTIVE')",
                categoryId, "Category " + categoryId);
    }

    private void seedInstructor(UUID instructorId) {
        jdbcTemplate.update("INSERT INTO instructors (id, name, email, biography) VALUES (?, ?, ?, NULL)",
                instructorId, "Instructor", instructorId + "@example.com");
    }

    private void seedStudent(UUID id, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO students (id, first_name, last_name, email, registration_timestamp)
                VALUES (?, ?, ?, ?, TIMESTAMPTZ '2026-08-30 12:00:00+00')
                """,
                id, name, "Surname", id + "@example.com"
        );
    }

    private void seedCourse(UUID id, UUID categoryId, UUID instructorId, String title) {
        jdbcTemplate.update("""
                INSERT INTO courses (
                    id, title, description, estimated_duration_hours, level, price_amount, currency_code,
                    maximum_seats, occupied_seats, status, category_id, instructor_id
                ) VALUES (?, ?, NULL, 10, 'BEGINNER', 25.00, 'USD', 30, 0, 'PUBLISHED', ?, ?)
                """, id, title, categoryId, instructorId);
    }

    private void seedEnrollment(UUID id, UUID studentId, UUID courseId, int progress) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (id, student_id, course_id, status, progress, enrolled_at, completed_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, TIMESTAMPTZ '2026-08-30 12:00:00+00', NULL)
                """, id, studentId, courseId, progress);
    }
}
