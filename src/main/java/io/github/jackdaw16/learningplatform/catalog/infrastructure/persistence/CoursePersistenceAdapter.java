package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.catalog.application.CourseSearchCriteria;
import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.shared.Money;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class CoursePersistenceAdapter implements CourseRepository {

    private final SpringDataCourseRepository repository;

    public CoursePersistenceAdapter(SpringDataCourseRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Course course) {
        repository.save(new CourseJpaEntity(
                course.id(),
                course.title(),
                course.description(),
                course.estimatedDurationHours(),
                course.level(),
                course.price().amount(),
                course.price().currency().getCurrencyCode(),
                course.maximumSeats(),
                course.occupiedSeats(),
                course.status(),
                course.categoryId(),
                course.instructorId()
        ));
    }

    @Override
    public Optional<Course> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Course> search(CourseSearchCriteria criteria, PageQuery pageQuery) {
        Page<CourseJpaEntity> page = repository.findAll(specificationFor(criteria), toPageable(criteria, pageQuery));
        return new PageResult<>(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private Course toDomain(CourseJpaEntity entity) {
        return Course.rehydrate(
                entity.id(),
                entity.title(),
                entity.description(),
                entity.estimatedDurationHours(),
                entity.level(),
                new Money(entity.priceAmount(), Currency.getInstance(entity.currencyCode())),
                entity.maximumSeats(),
                entity.occupiedSeats(),
                entity.status(),
                entity.categoryId(),
                entity.instructorId()
        );
    }

    @Override
    public boolean existsByCategoryId(UUID categoryId) {
        return repository.existsByCategoryId(categoryId);
    }

    @Override
    public boolean existsByInstructorId(UUID instructorId) {
        return repository.existsByInstructorId(instructorId);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private Specification<CourseJpaEntity> specificationFor(CourseSearchCriteria criteria) {
        Specification<CourseJpaEntity> specification = Specification.unrestricted();
        if (criteria.categoryId() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("categoryId"), criteria.categoryId()));
        }
        if (criteria.level() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("level"), criteria.level()));
        }
        if (criteria.currency() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(
                    root.get("currencyCode"), criteria.currency().getCurrencyCode()
            ));
        }
        if (criteria.minPrice() != null) {
            specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(
                    root.<java.math.BigDecimal>get("priceAmount"), criteria.minPrice()
            ));
        }
        if (criteria.maxPrice() != null) {
            specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(
                    root.<java.math.BigDecimal>get("priceAmount"), criteria.maxPrice()
            ));
        }
        if (criteria.title() != null) {
            String pattern = "%" + escapeLikeValue(criteria.title().toLowerCase(Locale.ROOT)) + "%";
            specification = specification.and((root, query, builder) -> builder.like(
                    builder.lower(root.<String>get("title")), pattern, '\\'
            ));
        }
        if (Boolean.TRUE.equals(criteria.availableOnly())) {
            specification = specification.and((root, query, builder) -> builder.lessThan(
                    root.<Integer>get("occupiedSeats"), root.<Integer>get("maximumSeats")
            ));
        }
        return specification;
    }

    private PageRequest toPageable(CourseSearchCriteria criteria, PageQuery pageQuery) {
        String property = switch (pageQuery.sortField()) {
            case "title" -> "title";
            case "price" -> "priceAmount";
            case "level" -> "level";
            case "estimatedDurationHours" -> "estimatedDurationHours";
            default -> throw new IllegalArgumentException("Unsupported course sort field: " + pageQuery.sortField());
        };
        if ("price".equals(pageQuery.sortField()) && criteria.currency() == null) {
            throw new IllegalArgumentException("Currency is required when sorting courses by price");
        }
        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort(property, pageQuery.sortDirection()));
    }

    private String escapeLikeValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Sort sort(String property, SortDirection sortDirection) {
        return Sort.by(toSpringDirection(sortDirection), property)
                .and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private Sort.Direction toSpringDirection(SortDirection sortDirection) {
        return switch (sortDirection) {
            case ASC -> Sort.Direction.ASC;
            case DESC -> Sort.Direction.DESC;
        };
    }
}
