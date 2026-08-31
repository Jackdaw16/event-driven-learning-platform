package io.github.jackdaw16.learningplatform.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jackdaw16.learningplatform.auth.application.port.AuthUserRepository;
import io.github.jackdaw16.learningplatform.auth.domain.AuthRole;
import io.github.jackdaw16.learningplatform.auth.domain.AuthUser;
import java.util.UUID;
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
class AuthUserPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private AuthUserRepository authUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_users");
    }

    @Test
    void findsUserByUsername() {
        UUID principalId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        String passwordHash = "$2a$10$abcdefghijklmnopqrstuv1234567890abcdefghijklmnopqrstuv";
        jdbcTemplate.update(
                "INSERT INTO auth_users (id, username, password_hash, role, principal_id) VALUES (?, ?, ?, ?, ?)",
                id,
                "student-user",
                passwordHash,
                "STUDENT",
                principalId
        );

        AuthUser authUser = authUserRepository.findByUsername("student-user").orElseThrow();

        assertEquals(new AuthUser(id, "student-user", passwordHash, AuthRole.STUDENT, principalId), authUser);
        assertTrue(authUserRepository.findByUsername("missing-user").isEmpty());
    }
}
