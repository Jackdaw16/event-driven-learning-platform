package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.application.CourseService;
import io.github.jackdaw16.learningplatform.catalog.application.CreateCourseCommand;
import io.github.jackdaw16.learningplatform.catalog.application.UpdateCourseCommand;
import io.github.jackdaw16.learningplatform.shared.Money;
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
@RequestMapping("/api/courses")
public class CourseController {

    private static final Set<String> SORT_FIELDS = Set.of("title", "price", "level", "estimatedDurationHours");

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseResponse.from(courseService.create(createCommand(request))));
    }

    @GetMapping("/{id}")
    public CourseResponse getById(@PathVariable UUID id) {
        return CourseResponse.from(courseService.getById(id));
    }

    @GetMapping
    public PageResponse<CourseResponse> search(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        var criteria = CatalogHttpRequestParser.courseSearchCriteria(
                categoryId, level, currency, minPrice, maxPrice, title, availableOnly
        );
        var pageQuery = CatalogHttpRequestParser.pageQuery(page, size, sort, "title", SORT_FIELDS);
        if ("price".equals(pageQuery.sortField()) && criteria.currency() == null) {
            throw new IllegalArgumentException("Currency is required when sorting courses by price");
        }
        return PageResponse.from(
                courseService.search(criteria, pageQuery),
                CourseResponse::from
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        return CourseResponse.from(courseService.update(id, updateCommand(request)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public CourseResponse publish(@PathVariable UUID id) {
        return CourseResponse.from(courseService.publish(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public CourseResponse archive(@PathVariable UUID id) {
        return CourseResponse.from(courseService.archive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private CreateCourseCommand createCommand(CourseRequest request) {
        return new CreateCourseCommand(
                request.title(),
                request.description(),
                request.estimatedDurationHours(),
                request.level(),
                money(request),
                request.maximumSeats(),
                request.categoryId(),
                request.instructorId()
        );
    }

    private UpdateCourseCommand updateCommand(CourseRequest request) {
        return new UpdateCourseCommand(
                request.title(),
                request.description(),
                request.estimatedDurationHours(),
                request.level(),
                money(request),
                request.maximumSeats(),
                request.categoryId(),
                request.instructorId()
        );
    }

    private Money money(CourseRequest request) {
        return new Money(request.priceAmount(), CatalogHttpRequestParser.currency(request.currency()));
    }
}
