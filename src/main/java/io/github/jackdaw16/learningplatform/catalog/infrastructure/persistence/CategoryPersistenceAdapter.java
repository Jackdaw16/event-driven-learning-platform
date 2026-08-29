package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class CategoryPersistenceAdapter implements CategoryRepository {

    private final SpringDataCategoryRepository repository;

    public CategoryPersistenceAdapter(SpringDataCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Category category) {
        repository.save(new CategoryJpaEntity(category.id(), category.name(), category.description(), category.status()));
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return repository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<Category> findByName(String name) {
        return repository.findByName(name)
                .map(this::toDomain);
    }

    @Override
    public PageResult<Category> list(PageQuery pageQuery) {
        Page<CategoryJpaEntity> page = repository.findAll(toPageable(pageQuery));
        return new PageResult<>(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private Category toDomain(CategoryJpaEntity entity) {
        return Category.rehydrate(entity.id(), entity.name(), entity.description(), entity.status());
    }

    private PageRequest toPageable(PageQuery pageQuery) {
        String property = switch (pageQuery.sortField()) {
            case "name" -> "name";
            default -> throw new IllegalArgumentException("Unsupported category sort field: " + pageQuery.sortField());
        };
        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort(property, pageQuery.sortDirection()));
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
