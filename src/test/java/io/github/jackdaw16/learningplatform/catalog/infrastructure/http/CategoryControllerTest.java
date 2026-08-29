package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jackdaw16.learningplatform.catalog.application.CategoryService;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.CategoryStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CategoryControllerTest {

    private MockMvc mockMvc;

    private final CategoryService categoryService = mock(CategoryService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryController(categoryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsCategoryAndReturnsCreatedResponse() throws Exception {
        Category category = category("Programming", "Software development courses", CategoryStatus.ACTIVE);
        when(categoryService.create("Programming", "Software development courses")).thenReturn(category);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Programming","description":"Software development courses"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(category.id().toString()))
                .andExpect(jsonPath("$.name").value("Programming"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(categoryService).create("Programming", "Software development courses");
    }

    @Test
    void rejectsBlankCategoryNameBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"description\":\"Description\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(categoryService);
    }

    @Test
    void getsCategoryAndMapsItsResponse() throws Exception {
        Category category = category("Programming", "Software development courses", CategoryStatus.ACTIVE);
        when(categoryService.getById(category.id())).thenReturn(category);

        mockMvc.perform(get("/api/categories/{id}", category.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.id().toString()))
                .andExpect(jsonPath("$.description").value("Software development courses"));

        verify(categoryService).getById(category.id());
    }

    @Test
    void returnsNotFoundProblemWhenCategoryDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryService.getById(id)).thenThrow(new ResourceNotFoundException("Category", id));

        mockMvc.perform(get("/api/categories/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void mapsPagedCategoryListAndConvertsSortDirection() throws Exception {
        Category category = category("Programming", null, CategoryStatus.ARCHIVED);
        PageQuery pageQuery = new PageQuery(1, 5, "name", SortDirection.DESC);
        when(categoryService.list(pageQuery)).thenReturn(new PageResult<>(List.of(category), 1, 5, 6, 2));

        mockMvc.perform(get("/api/categories")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "name,DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(category.id().toString()))
                .andExpect(jsonPath("$.content[0].status").value("ARCHIVED"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(categoryService).list(pageQuery);
    }

    @Test
    void delegatesCategoryArchiveToService() throws Exception {
        Category category = category("Programming", null, CategoryStatus.ARCHIVED);
        when(categoryService.archive(category.id())).thenReturn(category);

        mockMvc.perform(post("/api/categories/{id}/archive", category.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(categoryService).archive(category.id());
    }

    @Test
    void deletesCategoryWithNoContentResponse() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(id);
    }

    @Test
    void returnsConflictProblemForCategoryConflict() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ConflictException("Category is referenced by courses")).when(categoryService).delete(id);

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void hidesDatabaseDetailsForDataIntegrityConflicts() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("ERROR: violates foreign key constraint courses_category_id_fkey"))
                .when(categoryService).delete(id);

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("The operation conflicts with existing data."))
                .andExpect(content().string(not(containsString("courses_category_id_fkey"))));
    }

    private Category category(String name, String description, CategoryStatus status) {
        return Category.rehydrate(UUID.randomUUID(), name, description, status);
    }
}
