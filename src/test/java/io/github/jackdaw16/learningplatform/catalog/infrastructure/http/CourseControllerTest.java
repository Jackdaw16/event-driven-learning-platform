package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.jackdaw16.learningplatform.auth.infrastructure.security.OwnershipAuthorization;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jackdaw16.learningplatform.catalog.application.CourseSearchCriteria;
import io.github.jackdaw16.learningplatform.catalog.application.CourseService;
import io.github.jackdaw16.learningplatform.catalog.application.CreateCourseCommand;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.application.UpdateCourseCommand;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CourseControllerTest {

    private MockMvc mockMvc;

    private final CourseService courseService = mock(CourseService.class);
    private final OwnershipAuthorization ownershipAuthorization = mock(OwnershipAuthorization.class);

    private final UUID categoryId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseController(courseService, ownershipAuthorization))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsCourseAndConvertsPriceToMoneyAtHttpBoundary() throws Exception {
        Course course = course("Java Foundations", new BigDecimal("49.99"), CourseStatus.DRAFT, 0);
        when(courseService.create(any(CreateCourseCommand.class))).thenReturn(course);

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson("Java Foundations", "49.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(course.id().toString()))
                .andExpect(jsonPath("$.priceAmount").value(49.99))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.occupiedSeats").value(0))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        ArgumentCaptor<CreateCourseCommand> command = ArgumentCaptor.forClass(CreateCourseCommand.class);
        verify(courseService).create(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                new CreateCourseCommand(
                        "Java Foundations",
                        "A practical introduction",
                        12,
                        CourseLevel.BEGINNER,
                        new Money(new BigDecimal("49.99"), Currency.getInstance("USD")),
                        30,
                        categoryId,
                        instructorId
                ),
                command.getValue()
        );
    }

    @Test
    void rejectsInvalidCourseRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(" ", "49.99")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(courseService);
    }

    @Test
    void getsCourseAndMapsItsResponse() throws Exception {
        UUID id = UUID.randomUUID();
        Course course = course(id, "Java Foundations", new BigDecimal("49.99"), CourseStatus.PUBLISHED, 2);
        when(courseService.getById(id)).thenReturn(course);

        mockMvc.perform(get("/api/courses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.level").value("BEGINNER"))
                .andExpect(jsonPath("$.maximumSeats").value(30))
                .andExpect(jsonPath("$.occupiedSeats").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(courseService).getById(id);
    }

    @Test
    void updatesCourseWithConvertedCommand() throws Exception {
        UUID id = UUID.randomUUID();
        Course updated = course(id, "Advanced Java", new BigDecimal("99.50"), CourseStatus.DRAFT, 0);
        when(courseService.update(org.mockito.ArgumentMatchers.eq(id), any(UpdateCourseCommand.class))).thenReturn(updated);

        mockMvc.perform(put("/api/courses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson("Advanced Java", "99.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Advanced Java"));

        ArgumentCaptor<UpdateCourseCommand> command = ArgumentCaptor.forClass(UpdateCourseCommand.class);
        verify(courseService).update(org.mockito.ArgumentMatchers.eq(id), command.capture());
        verify(ownershipAuthorization).requireCourseUpdate(id, instructorId);
        org.junit.jupiter.api.Assertions.assertEquals(
                new UpdateCourseCommand(
                        "Advanced Java",
                        "A practical introduction",
                        12,
                        CourseLevel.BEGINNER,
                        new Money(new BigDecimal("99.50"), Currency.getInstance("USD")),
                        30,
                        categoryId,
                        instructorId
                ),
                command.getValue()
        );
    }

    @Test
    void delegatesCoursePublishToService() throws Exception {
        UUID id = UUID.randomUUID();
        when(courseService.publish(id)).thenReturn(course(id, "Java Foundations", new BigDecimal("49.99"), CourseStatus.PUBLISHED, 0));

        mockMvc.perform(post("/api/courses/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(courseService).publish(id);
    }

    @Test
    void delegatesCourseArchiveToService() throws Exception {
        UUID id = UUID.randomUUID();
        when(courseService.archive(id)).thenReturn(course(id, "Java Foundations", new BigDecimal("49.99"), CourseStatus.ARCHIVED, 0));

        mockMvc.perform(post("/api/courses/{id}/archive", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(courseService).archive(id);
    }

    @Test
    void deletesCourseWithNoContentResponse() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/courses/{id}", id))
                .andExpect(status().isNoContent());

        verify(courseService).delete(id);
    }

    @Test
    void returnsConflictProblemForInvalidCourseLifecycleTransition() throws Exception {
        UUID id = UUID.randomUUID();
        when(courseService.publish(id)).thenThrow(new IllegalStateException("Only draft courses can be published"));

        mockMvc.perform(post("/api/courses/{id}/publish", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Invalid state transition"));
    }

    @Test
    void convertsEveryCourseSearchCriterionAndPageQuery() throws Exception {
        CourseSearchCriteria criteria = new CourseSearchCriteria(
                categoryId,
                CourseLevel.ADVANCED,
                Currency.getInstance("USD"),
                new BigDecimal("10.50"),
                new BigDecimal("99.99"),
                "java",
                true
        );
        PageQuery pageQuery = new PageQuery(2, 25, "price", SortDirection.DESC);
        when(courseService.search(criteria, pageQuery)).thenReturn(new PageResult<>(List.of(), 2, 25, 0, 0));

        mockMvc.perform(get("/api/courses")
                        .param("categoryId", categoryId.toString())
                        .param("level", "ADVANCED")
                        .param("currency", "USD")
                        .param("minPrice", "10.50")
                        .param("maxPrice", "99.99")
                        .param("title", "java")
                        .param("availableOnly", "TRUE")
                        .param("page", "2")
                        .param("size", "25")
                        .param("sort", "price,dEsC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(25));

        verify(courseService).search(criteria, pageQuery);
    }

    @Test
    void rejectsMalformedCourseSort() throws Exception {
        mockMvc.perform(get("/api/courses").param("sort", "title"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(courseService);
    }

    @Test
    void rejectsUnsupportedCourseSort() throws Exception {
        mockMvc.perform(get("/api/courses").param("sort", "status,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(courseService);
    }

    @Test
    void rejectsInvalidCurrencyInSearch() throws Exception {
        mockMvc.perform(get("/api/courses").param("currency", "NOT_A_CURRENCY"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(courseService);
    }

    @Test
    void rejectsPriceFilterWithoutCurrency() throws Exception {
        mockMvc.perform(get("/api/courses").param("minPrice", "10.00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(courseService);
    }

    @Test
    void rejectsPriceSortWithoutCurrency() throws Exception {
        mockMvc.perform(get("/api/courses").param("sort", "price,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(courseService);
    }

    @Test
    void rejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/courses").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(courseService);
    }

    @Test
    void rejectsInvalidSearchValueConversions() throws Exception {
        mockMvc.perform(get("/api/courses").param("categoryId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("level", "EXPERT"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("minPrice", "not-a-number").param("currency", "USD"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("availableOnly", "yes"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courseService);
    }

    private Course course(String title, BigDecimal price, CourseStatus status, int occupiedSeats) {
        return course(UUID.randomUUID(), title, price, status, occupiedSeats);
    }

    private Course course(UUID id, String title, BigDecimal price, CourseStatus status, int occupiedSeats) {
        return Course.rehydrate(
                id,
                title,
                "A practical introduction",
                12,
                CourseLevel.BEGINNER,
                new Money(price, Currency.getInstance("USD")),
                30,
                occupiedSeats,
                status,
                categoryId,
                instructorId
        );
    }

    private String courseRequestJson(String title, String priceAmount) {
        return """
                {
                  "title":"%s",
                  "description":"A practical introduction",
                  "estimatedDurationHours":12,
                  "level":"BEGINNER",
                  "priceAmount":%s,
                  "currency":"USD",
                  "maximumSeats":30,
                  "categoryId":"%s",
                  "instructorId":"%s"
                }
                """.formatted(title, priceAmount, categoryId, instructorId);
    }
}
