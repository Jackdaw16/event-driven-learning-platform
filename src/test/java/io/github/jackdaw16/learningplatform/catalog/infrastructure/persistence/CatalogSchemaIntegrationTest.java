package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CatalogSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static int migrationsExecuted;
    private static List<String> migrationVersions;

    @BeforeAll
    static void migratesCleanDatabase() throws SQLException {
        var result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        migrationsExecuted = result.migrationsExecuted;
        migrationVersions = appliedMigrationVersions();
    }

    @BeforeEach
    void clearCatalogData() throws SQLException {
        execute("TRUNCATE TABLE courses, categories, instructors");
    }

    @Test
    void appliesV1AndV2ToACleanDatabase() {
        assertEquals(2, migrationsExecuted);
        assertEquals(List.of("1", "2"), migrationVersions);
    }

    @Test
    void rejectsDuplicateCategoryNames() throws SQLException {
        execute("INSERT INTO categories (id, name, description, status) VALUES (?, 'Programming', NULL, 'ACTIVE')", UUID.randomUUID());

        assertThrows(
                SQLException.class,
                () -> execute(
                        "INSERT INTO categories (id, name, description, status) VALUES (?, 'Programming', NULL, 'ACTIVE')",
                        UUID.randomUUID()
                )
        );
    }

    @Test
    void rejectsDuplicateInstructorEmails() throws SQLException {
        execute(
                "INSERT INTO instructors (id, name, email, biography) VALUES (?, 'Ada Lovelace', 'ada@example.com', NULL)",
                UUID.randomUUID()
        );

        assertThrows(
                SQLException.class,
                () -> execute(
                        "INSERT INTO instructors (id, name, email, biography) VALUES (?, 'Another Ada', 'ada@example.com', NULL)",
                        UUID.randomUUID()
                )
        );
    }

    @Test
    void rejectsCoursesWithUnknownCategoryOrInstructor() throws SQLException {
        UUID instructorId = UUID.randomUUID();
        insertInstructor(instructorId);

        assertThrows(SQLException.class, () -> insertCourse(UUID.randomUUID(), instructorId, 10, 0, "DRAFT", "BEGINNER", new BigDecimal("49.99")));

        UUID categoryId = UUID.randomUUID();
        insertCategory(categoryId);

        assertThrows(SQLException.class, () -> insertCourse(categoryId, UUID.randomUUID(), 10, 0, "DRAFT", "BEGINNER", new BigDecimal("49.99")));
    }

    @Test
    void rejectsInvalidCapacityStates() throws SQLException {
        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        insertCategory(categoryId);
        insertInstructor(instructorId);

        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, 0, 0, "DRAFT", "BEGINNER", new BigDecimal("49.99")));
        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, -1, 0, "DRAFT", "BEGINNER", new BigDecimal("49.99")));
        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, 10, -1, "PUBLISHED", "BEGINNER", new BigDecimal("49.99")));
        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, 10, 11, "PUBLISHED", "BEGINNER", new BigDecimal("49.99")));
    }

    @Test
    void rejectsDraftCoursesWithOccupiedSeats() throws SQLException {
        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        insertCategory(categoryId);
        insertInstructor(instructorId);

        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, 10, 1, "DRAFT", "BEGINNER", new BigDecimal("49.99")));
    }

    @Test
    void rejectsInvalidCategoryCourseStatusAndCourseLevel() throws SQLException {
        assertThrows(
                SQLException.class,
                () -> execute("INSERT INTO categories (id, name, description, status) VALUES (?, 'Programming', NULL, 'RETIRED')", UUID.randomUUID())
        );

        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        insertCategory(categoryId);
        insertInstructor(instructorId);

        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, 10, 0, "RETIRED", "BEGINNER", new BigDecimal("49.99")));
        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, 10, 0, "DRAFT", "EXPERT", new BigDecimal("49.99")));
    }

    @Test
    void rejectsNegativeCoursePrices() throws SQLException {
        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        insertCategory(categoryId);
        insertInstructor(instructorId);

        assertThrows(SQLException.class, () -> insertCourse(categoryId, instructorId, 10, 0, "DRAFT", "BEGINNER", new BigDecimal("-0.01")));
    }

    @Test
    void restrictsDeletionOfReferencedCategories() throws SQLException {
        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        insertCategory(categoryId);
        insertInstructor(instructorId);
        insertCourse(categoryId, instructorId, 10, 0, "DRAFT", "BEGINNER", new BigDecimal("49.99"));

        assertThrows(SQLException.class, () -> execute("DELETE FROM categories WHERE id = ?", categoryId));
    }

    @Test
    void restrictsDeletionOfReferencedInstructors() throws SQLException {
        UUID categoryId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        insertCategory(categoryId);
        insertInstructor(instructorId);
        insertCourse(categoryId, instructorId, 10, 0, "DRAFT", "BEGINNER", new BigDecimal("49.99"));

        assertThrows(SQLException.class, () -> execute("DELETE FROM instructors WHERE id = ?", instructorId));
    }

    private static List<String> appliedMigrationVersions() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"
                );
                ResultSet resultSet = statement.executeQuery()) {
            List<String> versions = new ArrayList<>();
            while (resultSet.next()) {
                versions.add(resultSet.getString("version"));
            }
            return versions;
        }
    }

    private void insertCategory(UUID id) throws SQLException {
        execute("INSERT INTO categories (id, name, description, status) VALUES (?, ?, NULL, 'ACTIVE')", id, "Category " + id);
    }

    private void insertInstructor(UUID id) throws SQLException {
        execute(
                "INSERT INTO instructors (id, name, email, biography) VALUES (?, ?, ?, NULL)",
                id,
                "Instructor " + id,
                id + "@example.com"
        );
    }

    private void insertCourse(
            UUID categoryId,
            UUID instructorId,
            int maximumSeats,
            int occupiedSeats,
            String status,
            String level,
            BigDecimal priceAmount
    ) throws SQLException {
        execute(
                """
                INSERT INTO courses (
                    id, title, description, estimated_duration_hours, level, price_amount, currency_code,
                    maximum_seats, occupied_seats, status, category_id, instructor_id
                ) VALUES (?, 'Java 21 Fundamentals', NULL, 12, ?, ?, 'USD', ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                level,
                priceAmount,
                maximumSeats,
                occupiedSeats,
                status,
                categoryId,
                instructorId
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
}
