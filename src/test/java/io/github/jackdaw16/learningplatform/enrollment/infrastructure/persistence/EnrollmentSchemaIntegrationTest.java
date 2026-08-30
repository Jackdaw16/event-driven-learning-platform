package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class EnrollmentSchemaIntegrationTest {

    private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll
    static void migratesCleanDatabase() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void clearEnrollmentData() throws SQLException {
        execute("TRUNCATE TABLE certificates, payments, enrollments, students, courses, categories, instructors");
    }

    @Test
    void rejectsDuplicateEmailsAndBlankStudentFields() throws SQLException {
        insertStudent(uuid(1));

        assertThrows(SQLException.class, () -> insertStudent(uuid(2), "First", "Last", "student-1@example.com"));
        assertThrows(SQLException.class, () -> insertStudent(uuid(3), "", "Last", "student-3@example.com"));
        assertThrows(SQLException.class, () -> insertStudent(uuid(4), "First", "", "student-4@example.com"));
        assertThrows(SQLException.class, () -> insertStudent(uuid(5), "First", "Last", ""));
    }

    @Test
    void rejectsEnrollmentsWithUnknownStudentOrCourse() throws SQLException {
        UUID studentId = uuid(1);
        UUID courseId = uuid(2);
        insertStudent(studentId);
        insertCourse(courseId);

        assertThrows(SQLException.class, () -> insertEnrollment(uuid(3), uuid(4), courseId, "PENDING_PAYMENT", 0, null));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(5), studentId, uuid(6), "PENDING_PAYMENT", 0, null));
    }

    @Test
    void rejectsInvalidEnrollmentStatusAndProgress() throws SQLException {
        UUID studentId = uuid(1);
        UUID courseId = uuid(2);
        insertStudent(studentId);
        insertCourse(courseId);

        assertThrows(SQLException.class, () -> insertEnrollment(uuid(3), studentId, courseId, "SUSPENDED", 0, null));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(4), studentId, courseId, "PENDING_PAYMENT", -1, null));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(5), studentId, courseId, "PENDING_PAYMENT", 101, null));
    }

    @Test
    void rejectsInconsistentEnrollmentLifecycleStates() throws SQLException {
        UUID studentId = uuid(1);
        UUID courseId = uuid(2);
        insertStudent(studentId);
        insertCourse(courseId);

        assertThrows(SQLException.class, () -> insertEnrollment(uuid(3), studentId, courseId, "PENDING_PAYMENT", 1, null));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(4), studentId, courseId, "PENDING_PAYMENT", 0, COMPLETED_AT));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(5), studentId, courseId, "ACTIVE", 100, null));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(6), studentId, courseId, "ACTIVE", 99, COMPLETED_AT));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(7), studentId, courseId, "COMPLETED", 99, COMPLETED_AT));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(8), studentId, courseId, "COMPLETED", 100, null));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(9), studentId, courseId, "CANCELLED", 100, null));
        assertThrows(SQLException.class, () -> insertEnrollment(uuid(10), studentId, courseId, "CANCELLED", 99, COMPLETED_AT));
    }

    @Test
    void rejectsSecondLiveEnrollmentForEachLiveStatus() throws SQLException {
        assertSecondLiveEnrollmentRejected(10, "PENDING_PAYMENT", 0, null);
        assertSecondLiveEnrollmentRejected(20, "ACTIVE", 0, null);
        assertSecondLiveEnrollmentRejected(30, "COMPLETED", 100, COMPLETED_AT);
    }

    @Test
    void allowsLiveEnrollmentAfterCancellation() throws SQLException {
        UUID studentId = uuid(1);
        UUID courseId = uuid(2);
        insertStudent(studentId);
        insertCourse(courseId);
        insertEnrollment(uuid(3), studentId, courseId, "CANCELLED", 0, null);

        insertEnrollment(uuid(4), studentId, courseId, "ACTIVE", 0, null);
    }

    @Test
    void rejectsPaymentsWithUnknownEnrollmentAndSecondPaymentPerEnrollment() throws SQLException {
        assertThrows(SQLException.class, () -> insertPayment(uuid(1), uuid(2), new BigDecimal("10.00"), "USD", "PENDING", "payment-1"));

        UUID enrollmentId = insertActiveEnrollment(10);
        insertPayment(uuid(13), enrollmentId, new BigDecimal("10.00"), "USD", "PENDING", "payment-2");

        assertThrows(
                SQLException.class,
                () -> insertPayment(uuid(14), enrollmentId, new BigDecimal("20.00"), "USD", "CONFIRMED", "payment-3")
        );
    }

    @Test
    void rejectsDuplicateOrBlankPaymentIdempotencyKeys() throws SQLException {
        UUID firstEnrollmentId = insertActiveEnrollment(10);
        UUID secondEnrollmentId = insertActiveEnrollment(20);
        insertPayment(uuid(13), firstEnrollmentId, new BigDecimal("10.00"), "USD", "PENDING", "payment-key");

        assertThrows(
                SQLException.class,
                () -> insertPayment(uuid(23), secondEnrollmentId, new BigDecimal("10.00"), "USD", "PENDING", "payment-key")
        );
        assertThrows(
                SQLException.class,
                () -> insertPayment(uuid(24), secondEnrollmentId, new BigDecimal("10.00"), "USD", "PENDING", " ")
        );
    }

    @Test
    void rejectsInvalidPaymentValuesAndAcceptsZeroAmount() throws SQLException {
        UUID enrollmentId = insertActiveEnrollment(10);

        assertThrows(
                SQLException.class,
                () -> insertPayment(uuid(13), enrollmentId, new BigDecimal("-0.01"), "USD", "PENDING", "payment-negative")
        );
        assertThrows(
                SQLException.class,
                () -> insertPayment(uuid(14), enrollmentId, BigDecimal.ZERO, "USD", "REVERSED", "payment-status")
        );
        assertThrows(
                SQLException.class,
                () -> insertPayment(uuid(15), enrollmentId, BigDecimal.ZERO, "US", "PENDING", "payment-currency-short")
        );
        assertThrows(
                SQLException.class,
                () -> insertPayment(uuid(16), enrollmentId, BigDecimal.ZERO, "USDD", "PENDING", "payment-currency-long")
        );

        assertDoesNotThrow(
                () -> insertPayment(uuid(17), enrollmentId, BigDecimal.ZERO, "USD", "PENDING", "payment-zero")
        );
    }

    private void assertSecondLiveEnrollmentRejected(long seed, String status, int progress, OffsetDateTime completedAt) throws SQLException {
        UUID studentId = uuid(seed);
        UUID courseId = uuid(seed + 1);
        insertStudent(studentId);
        insertCourse(courseId);
        insertEnrollment(uuid(seed + 2), studentId, courseId, status, progress, completedAt);

        assertThrows(
                SQLException.class,
                () -> insertEnrollment(uuid(seed + 3), studentId, courseId, status, progress, completedAt)
        );
    }

    private UUID insertActiveEnrollment(long seed) throws SQLException {
        UUID studentId = uuid(seed);
        UUID courseId = uuid(seed + 1);
        UUID enrollmentId = uuid(seed + 2);
        insertStudent(studentId);
        insertCourse(courseId);
        insertEnrollment(enrollmentId, studentId, courseId, "ACTIVE", 0, null);
        return enrollmentId;
    }

    private void insertStudent(UUID id) throws SQLException {
        insertStudent(id, "First", "Last", "student-" + id.getLeastSignificantBits() + "@example.com");
    }

    private void insertStudent(UUID id, String firstName, String lastName, String email) throws SQLException {
        execute(
                "INSERT INTO students (id, first_name, last_name, email, registration_timestamp) VALUES (?, ?, ?, ?, TIMESTAMPTZ '2026-01-01 00:00:00+00')",
                id,
                firstName,
                lastName,
                email
        );
    }

    private void insertCourse(UUID courseId) throws SQLException {
        UUID categoryId = uuid(1_000 + courseId.getLeastSignificantBits());
        UUID instructorId = uuid(2_000 + courseId.getLeastSignificantBits());
        execute("INSERT INTO categories (id, name, description, status) VALUES (?, ?, NULL, 'ACTIVE')", categoryId, "Category " + categoryId);
        execute(
                "INSERT INTO instructors (id, name, email, biography) VALUES (?, ?, ?, NULL)",
                instructorId,
                "Instructor " + instructorId,
                "instructor-" + instructorId.getLeastSignificantBits() + "@example.com"
        );
        execute(
                """
                INSERT INTO courses (
                    id, title, description, estimated_duration_hours, level, price_amount, currency_code,
                    maximum_seats, occupied_seats, status, category_id, instructor_id
                ) VALUES (?, 'Schema Test Course', NULL, 12, 'BEGINNER', 10.00, 'USD', 10, 0, 'DRAFT', ?, ?)
                """,
                courseId,
                categoryId,
                instructorId
        );
    }

    private void insertEnrollment(
            UUID id, UUID studentId, UUID courseId, String status, int progress, OffsetDateTime completedAt
    ) throws SQLException {
        execute(
                """
                INSERT INTO enrollments (id, student_id, course_id, status, progress, enrolled_at, completed_at)
                VALUES (?, ?, ?, ?, ?, TIMESTAMPTZ '2026-01-01 00:00:00+00', ?)
                """,
                id,
                studentId,
                courseId,
                status,
                progress,
                completedAt
        );
    }

    private void insertPayment(
            UUID id, UUID enrollmentId, BigDecimal amount, String currencyCode, String status, String idempotencyKey
    ) throws SQLException {
        execute(
                """
                INSERT INTO payments (id, enrollment_id, amount, currency_code, status, idempotency_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, TIMESTAMPTZ '2026-01-01 00:00:00+00')
                """,
                id,
                enrollmentId,
                amount,
                currencyCode,
                status,
                idempotencyKey
        );
    }

    private static void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
