package io.github.jackdaw16.learningplatform.auth.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "messaging.outbox.poll-interval=1h",
        "management.health.rabbit.enabled=false"
})
@Testcontainers
class AuthSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID studentActorId;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean("springSecurityFilterChain", Filter.class))
                .build();
        jdbcTemplate.execute("TRUNCATE TABLE auth_users, certificates, payments, enrollments, students, courses, categories, instructors");

        insertUser("admin", "admin-pass", "ADMIN", null);
        insertUser("instructor", "instructor-pass", "INSTRUCTOR", UUID.randomUUID());
        studentActorId = UUID.randomUUID();
        insertUser("student", "student-pass", "STUDENT", studentActorId);
        seedCatalogData();
    }

    @Test
    void validCredentialsReturnBearerJwt() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin-pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresInSeconds").isNumber());
    }

    @Test
    void wrongPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-pass"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void missingCredentialsOrInvalidBodyReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void jwtContainsExpectedRoleAndActorId() throws Exception {
        String token = tokenFor("student", "student-pass");

        Jwt jwt = jwtDecoder.decode(token);

        assertEquals("student", jwt.getSubject());
        assertEquals("STUDENT", jwt.getClaimAsString("role"));
        assertEquals(studentActorId.toString(), jwt.getClaimAsString("actorId"));
        assertEquals("event-driven-learning-platform", jwt.getClaimAsString("iss"));
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
    }

    @Test
    void validJwtAuthenticatesSuccessfully() throws Exception {
        String token = tokenFor("student", "student-pass");

        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void missingBearerTokenOnProtectedApiReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void malformedJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void studentCallingAdminCatalogMutationReturnsForbidden() throws Exception {
        String token = tokenFor("student", "student-pass");

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Only Admin","description":"Restricted"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void adminCallingCatalogMutationReachesController() throws Exception {
        String token = tokenFor("admin", "admin-pass");

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Admin Category","description":"Created by admin"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Admin Category"));
    }

    @Test
    void instructorCanReachCourseMutationAuthorization() throws Exception {
        String token = tokenFor("instructor", "instructor-pass");

        mockMvc.perform(post("/api/courses/{id}/publish", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCanReachEnrollmentLifecycleAuthorization() throws Exception {
        String token = tokenFor("student", "student-pass");

        mockMvc.perform(post("/api/enrollments/{id}/cancel", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void documentationIsPublicAndTokenIssuanceIsExcludedFromBearerSecurity() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/token'].post.security").isEmpty());
    }

    @Test
    void metricsRequireAnAdministratorAndAreAvailableThroughActuator() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + tokenFor("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    private String tokenFor(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        return body.get("accessToken").toString();
    }

    private void insertUser(String username, String password, String role, UUID principalId) {
        jdbcTemplate.update(
                "INSERT INTO auth_users (id, username, password_hash, role, principal_id) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(password),
                role,
                principalId
        );
    }

    private void seedCatalogData() {
        transactionTemplate.executeWithoutResult(status -> {
            UUID categoryId = UUID.randomUUID();
            UUID instructorId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO categories (id, name, description, status) VALUES (?, ?, NULL, 'ACTIVE')",
                    categoryId,
                    "Existing Category"
            );
            jdbcTemplate.update(
                    "INSERT INTO instructors (id, name, email, biography) VALUES (?, ?, ?, NULL)",
                    instructorId,
                    "Existing Instructor",
                    "existing.instructor+" + instructorId + "@example.com"
            );
        });
    }
}
