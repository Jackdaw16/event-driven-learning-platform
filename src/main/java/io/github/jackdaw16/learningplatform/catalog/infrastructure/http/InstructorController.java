package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.application.InstructorService;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instructors")
public class InstructorController {

    private static final Set<String> SORT_FIELDS = Set.of("name", "email");

    private final InstructorService instructorService;

    public InstructorController(InstructorService instructorService) {
        this.instructorService = instructorService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InstructorResponse> create(@Valid @RequestBody InstructorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InstructorResponse.from(instructorService.create(request.name(), request.email(), request.biography())));
    }

    @GetMapping("/{id}")
    public InstructorResponse getById(@PathVariable UUID id) {
        return InstructorResponse.from(instructorService.getById(id));
    }

    @GetMapping
    public PageResponse<InstructorResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return PageResponse.from(
                instructorService.list(CatalogHttpRequestParser.pageQuery(page, size, sort, "name", SORT_FIELDS)),
                InstructorResponse::from
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InstructorResponse update(@PathVariable UUID id, @Valid @RequestBody InstructorRequest request) {
        return InstructorResponse.from(instructorService.update(id, request.name(), request.email(), request.biography()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        instructorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
