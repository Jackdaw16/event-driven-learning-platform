package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jackdaw16.learningplatform.catalog.application.InstructorService;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstructorControllerTest {

    private MockMvc mockMvc;

    private final InstructorService instructorService = mock(InstructorService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InstructorController(instructorService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsInstructorAndReturnsCreatedResponse() throws Exception {
        Instructor instructor = instructor("Ada Lovelace", "ada@example.com", "Mathematician");
        when(instructorService.create("Ada Lovelace", "ada@example.com", "Mathematician")).thenReturn(instructor);

        mockMvc.perform(post("/api/instructors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada Lovelace","email":"ada@example.com","biography":"Mathematician"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(instructor.id().toString()))
                .andExpect(jsonPath("$.email").value("ada@example.com"));

        verify(instructorService).create("Ada Lovelace", "ada@example.com", "Mathematician");
    }

    @Test
    void rejectsInvalidInstructorEmailBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/instructors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(instructorService);
    }

    @Test
    void getsAndUpdatesInstructorThroughService() throws Exception {
        Instructor instructor = instructor("Ada Lovelace", "ada@example.com", "Mathematician");
        when(instructorService.getById(instructor.id())).thenReturn(instructor);
        when(instructorService.update(instructor.id(), "Ada Byron", "ada@example.com", "Pioneer"))
                .thenReturn(instructor("Ada Byron", "ada@example.com", "Pioneer", instructor.id()));

        mockMvc.perform(get("/api/instructors/{id}", instructor.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"));
        mockMvc.perform(put("/api/instructors/{id}", instructor.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Byron\",\"email\":\"ada@example.com\",\"biography\":\"Pioneer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Byron"));

        verify(instructorService).getById(instructor.id());
        verify(instructorService).update(instructor.id(), "Ada Byron", "ada@example.com", "Pioneer");
    }

    @Test
    void mapsPagedInstructorListAndConvertsSortDirection() throws Exception {
        Instructor instructor = instructor("Ada Lovelace", "ada@example.com", null);
        PageQuery pageQuery = new PageQuery(0, 2, "email", SortDirection.DESC);
        when(instructorService.list(pageQuery)).thenReturn(new PageResult<>(List.of(instructor), 0, 2, 1, 1));

        mockMvc.perform(get("/api/instructors")
                        .param("size", "2")
                        .param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("ada@example.com"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(instructorService).list(pageQuery);
    }

    @Test
    void deletesInstructorWithNoContentResponse() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/instructors/{id}", id))
                .andExpect(status().isNoContent());

        verify(instructorService).delete(id);
    }

    @Test
    void returnsConflictProblemForReferencedInstructor() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ConflictException("Instructor is referenced by courses")).when(instructorService).delete(id);

        mockMvc.perform(delete("/api/instructors/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    private Instructor instructor(String name, String email, String biography) {
        return instructor(name, email, biography, UUID.randomUUID());
    }

    private Instructor instructor(String name, String email, String biography, UUID id) {
        return new Instructor(id, name, email, biography);
    }
}
