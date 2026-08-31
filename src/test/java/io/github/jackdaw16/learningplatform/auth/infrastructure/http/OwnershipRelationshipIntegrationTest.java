package io.github.jackdaw16.learningplatform.auth.infrastructure.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.Filter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnershipRelationshipIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private UUID categoryId;
    private UUID instructorAId;
    private UUID instructorBId;
    private UUID studentAId;
    private UUID studentBId;
    private UUID missingStudentId;
    private UUID instructorACourseId;
    private UUID instructorBCourseId;
    private UUID publishedCourseId;
    private UUID studentAEnrollmentId;
    private UUID studentBEnrollmentId;

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

        categoryId = UUID.randomUUID();
        instructorAId = UUID.randomUUID();
        instructorBId = UUID.randomUUID();
        studentAId = UUID.randomUUID();
        studentBId = UUID.randomUUID();
        missingStudentId = UUID.randomUUID();
        instructorACourseId = UUID.randomUUID();
        instructorBCourseId = UUID.randomUUID();
        publishedCourseId = UUID.randomUUID();
        studentAEnrollmentId = UUID.randomUUID();
        studentBEnrollmentId = UUID.randomUUID();

        insertUser("admin", "admin-pass", "ADMIN", null);
        insertUser("instructor-a", "instructor-pass", "INSTRUCTOR", instructorAId);
        insertUser("instructor-b", "instructor-pass", "INSTRUCTOR", instructorBId);
        insertUser("student-a", "student-pass", "STUDENT", studentAId);
        insertUser("student-b", "student-pass", "STUDENT", studentBId);
        insertUser("student-missing", "student-pass", "STUDENT", missingStudentId);
        seedData();
    }

    @Test
    void studentCanCreateOnlyTheirOwnEnrollment() throws Exception {
        UUID enrolmentCourseId = insertCourse(instructorAId, "Student enrollment course", "PUBLISHED");

        mockMvc.perform(createEnrollment(studentAId, enrolmentCourseId, "student-create")
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(studentAId.toString()));

        mockMvc.perform(createEnrollment(studentBId, enrolmentCourseId, "other-student-create")
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanUpdateAndCancelOnlyTheirOwnEnrollment() throws Exception {
        mockMvc.perform(updateProgress(studentAEnrollmentId, 55)
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(55));

        mockMvc.perform(updateProgress(studentBEnrollmentId, 55)
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", studentAEnrollmentId)
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentStatus").value("CANCELLED"));

        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", studentBEnrollmentId)
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanOperateOnAnyStudentEnrollment() throws Exception {
        UUID additionalCourseId = insertCourse(instructorAId, "Admin enrollment course", "PUBLISHED");
        String admin = bearer("admin", "admin-pass");

        mockMvc.perform(createEnrollment(studentBId, additionalCourseId, "admin-create").header("Authorization", admin))
                .andExpect(status().isCreated());
        mockMvc.perform(updateProgress(studentBEnrollmentId, 70).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(70));
        mockMvc.perform(post("/api/enrollments/{enrollmentId}/cancel", studentBEnrollmentId)
                        .header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    void instructorCanCreateOnlyCoursesOwnedByTheirActorId() throws Exception {
        mockMvc.perform(courseRequest(post("/api/courses"), instructorAId, "Instructor course")
                        .header("Authorization", bearer("instructor-a", "instructor-pass")))
                .andExpect(status().isCreated());

        mockMvc.perform(courseRequest(post("/api/courses"), instructorBId, "Other instructor course")
                        .header("Authorization", bearer("instructor-a", "instructor-pass")))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCanUpdateAndPublishOnlyPersistedOwnedCourses() throws Exception {
        String instructorA = bearer("instructor-a", "instructor-pass");
        String instructorB = bearer("instructor-b", "instructor-pass");

        mockMvc.perform(courseRequest(put("/api/courses/{id}", instructorACourseId), instructorAId, "Updated owned course")
                        .header("Authorization", instructorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated owned course"));
        mockMvc.perform(courseRequest(put("/api/courses/{id}", instructorACourseId), instructorBId, "Attempted update")
                        .header("Authorization", instructorB))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/courses/{id}/publish", instructorACourseId)
                        .header("Authorization", instructorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(post("/api/courses/{id}/publish", instructorBCourseId)
                        .header("Authorization", instructorA))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCannotReassignAnOwnedCourse() throws Exception {
        mockMvc.perform(courseRequest(put("/api/courses/{id}", instructorACourseId), instructorBId, "Attempted reassignment")
                        .header("Authorization", bearer("instructor-a", "instructor-pass")))
                .andExpect(status().isForbidden());

        UUID persistedInstructorId = jdbcTemplate.queryForObject(
                "SELECT instructor_id FROM courses WHERE id = ?", UUID.class, instructorACourseId
        );
        assertEquals(instructorAId, persistedInstructorId);
    }

    @Test
    void adminCanReassignAnyCourse() throws Exception {
        mockMvc.perform(courseRequest(put("/api/courses/{id}", instructorACourseId), instructorBId, "Admin reassigned course")
                        .header("Authorization", bearer("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructorId").value(instructorBId.toString()));

        UUID persistedInstructorId = jdbcTemplate.queryForObject(
                "SELECT instructor_id FROM courses WHERE id = ?", UUID.class, instructorACourseId
        );
        assertEquals(instructorBId, persistedInstructorId);
    }

    @Test
    void instructorCannotArchiveOrDeleteCourses() throws Exception {
        String instructorA = bearer("instructor-a", "instructor-pass");

        mockMvc.perform(post("/api/courses/{id}/archive", instructorACourseId).header("Authorization", instructorA))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/courses/{id}", instructorACourseId).header("Authorization", instructorA))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRetainsAllCourseMutations() throws Exception {
        String admin = bearer("admin", "admin-pass");
        MvcResult createResult = mockMvc.perform(courseRequest(post("/api/courses"), instructorAId, "Admin created course")
                        .header("Authorization", admin))
                .andExpect(status().isCreated())
                .andReturn();
        UUID createdCourseId = UUID.fromString(readBody(createResult).get("id").toString());

        mockMvc.perform(courseRequest(put("/api/courses/{id}", createdCourseId), instructorAId, "Admin updated course")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/{id}/publish", createdCourseId).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/{id}/archive", createdCourseId).header("Authorization", admin))
                .andExpect(status().isOk());

        UUID draftCourseId = insertCourse(instructorAId, "Admin deleted course", "DRAFT");
        mockMvc.perform(delete("/api/courses/{id}", draftCourseId).header("Authorization", admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminAndOwningInstructorCanListCourseStudents() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/students", publishedCourseId)
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearer("admin", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].studentId").isString())
                .andExpect(jsonPath("$.content[0].enrollmentId").isString())
                .andExpect(jsonPath("$.content[0].email").isString());

        mockMvc.perform(get("/api/courses/{courseId}/students", publishedCourseId)
                        .header("Authorization", bearer("instructor-a", "instructor-pass")))
                .andExpect(status().isOk());
    }

    @Test
    void nonOwningInstructorAndStudentCannotListCourseStudents() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/students", publishedCourseId)
                        .header("Authorization", bearer("instructor-b", "instructor-pass")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/courses/{courseId}/students", publishedCourseId)
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanListOnlyTheirOwnCoursesAndAdminCanListAnyStudentCourses() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/courses", studentAId)
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].courseId").isString())
                .andExpect(jsonPath("$.content[0].courseStatus").value("PUBLISHED"));
        mockMvc.perform(get("/api/students/{studentId}/courses", studentBId)
                        .header("Authorization", bearer("student-a", "student-pass")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/students/{studentId}/courses", studentBId)
                        .header("Authorization", bearer("admin", "admin-pass")))
                .andExpect(status().isOk());
    }

    @Test
    void authorizedRequestsForMissingStudentsReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/students/{studentId}/courses", missingStudentId)
                        .header("Authorization", bearer("admin", "admin-pass")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/students/{studentId}/courses", missingStudentId)
                        .header("Authorization", bearer("student-missing", "student-pass")))
                .andExpect(status().isNotFound());
    }

    private void seedData() {
        jdbcTemplate.update("INSERT INTO categories (id, name, description, status) VALUES (?, ?, ?, 'ACTIVE')",
                categoryId, "Category " + categoryId, "Category");
        insertInstructor(instructorAId, "Instructor A");
        insertInstructor(instructorBId, "Instructor B");
        insertStudent(studentAId, "Student A", "student-a@example.com");
        insertStudent(studentBId, "Student B", "student-b@example.com");
        insertCourse(instructorACourseId, instructorAId, "Instructor A draft", "DRAFT");
        insertCourse(instructorBCourseId, instructorBId, "Instructor B draft", "DRAFT");
        insertCourse(publishedCourseId, instructorAId, "Published course", "PUBLISHED");
        insertEnrollment(studentAEnrollmentId, studentAId, publishedCourseId, 40);
        insertEnrollment(studentBEnrollmentId, studentBId, publishedCourseId, 35);
        jdbcTemplate.update("UPDATE courses SET occupied_seats = 2 WHERE id = ?", publishedCourseId);
    }

    private void insertUser(String username, String password, String role, UUID principalId) {
        jdbcTemplate.update(
                "INSERT INTO auth_users (id, username, password_hash, role, principal_id) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), username, passwordEncoder.encode(password), role, principalId
        );
    }

    private void insertInstructor(UUID instructorId, String name) {
        jdbcTemplate.update("INSERT INTO instructors (id, name, email, biography) VALUES (?, ?, ?, NULL)",
                instructorId, name, instructorId + "@example.com");
    }

    private void insertStudent(UUID studentId, String name, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO students (id, first_name, last_name, email, registration_timestamp)
                VALUES (?, ?, ?, ?, TIMESTAMPTZ '2026-08-30 12:00:00+00')
                """,
                studentId, name, "Surname", email
        );
    }

    private UUID insertCourse(UUID instructorId, String title, String status) {
        UUID courseId = UUID.randomUUID();
        insertCourse(courseId, instructorId, title, status);
        return courseId;
    }

    private void insertCourse(UUID courseId, UUID instructorId, String title, String status) {
        jdbcTemplate.update("""
                INSERT INTO courses (
                    id, title, description, estimated_duration_hours, level, price_amount, currency_code,
                    maximum_seats, occupied_seats, status, category_id, instructor_id
                ) VALUES (?, ?, ?, 10, 'BEGINNER', 25.00, 'USD', 30, 0, ?, ?, ?)
                """, courseId, title, "Course description", status, categoryId, instructorId);
    }

    private void insertEnrollment(UUID enrollmentId, UUID studentId, UUID courseId, int progress) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (id, student_id, course_id, status, progress, enrolled_at, completed_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, TIMESTAMPTZ '2026-08-30 12:00:00+00', NULL)
                """, enrollmentId, studentId, courseId, progress);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createEnrollment(
            UUID studentId,
            UUID courseId,
            String idempotencyKey
    ) {
        return post("/api/students/{studentId}/enrollments", studentId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":\"%s\"}".formatted(courseId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateProgress(UUID enrollmentId, int progress) {
        return patch("/api/enrollments/{enrollmentId}/progress", enrollmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":%d}".formatted(progress));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder courseRequest(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            UUID instructorId,
            String title
    ) {
        return request.contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "title":"%s",
                  "description":"Course description",
                  "estimatedDurationHours":10,
                  "level":"BEGINNER",
                  "priceAmount":25.00,
                  "currency":"USD",
                  "maximumSeats":30,
                  "categoryId":"%s",
                  "instructorId":"%s"
                }
                """.formatted(title, categoryId, instructorId));
    }

    private String bearer(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + readBody(result).get("accessToken");
    }

    private Map<String, Object> readBody(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<Map<String, Object>>() {
        });
    }
}
