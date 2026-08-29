package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.catalog.application.PageQuery;
import io.github.jackdaw16.learningplatform.catalog.application.PageResult;
import io.github.jackdaw16.learningplatform.catalog.application.SortDirection;
import io.github.jackdaw16.learningplatform.catalog.application.port.InstructorRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Instructor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class InstructorPersistenceAdapter implements InstructorRepository {

    private final SpringDataInstructorRepository repository;

    public InstructorPersistenceAdapter(SpringDataInstructorRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Instructor instructor) {
        repository.save(new InstructorJpaEntity(instructor.id(), instructor.name(), instructor.email(), instructor.biography()));
    }

    @Override
    public Optional<Instructor> findById(UUID id) {
        return repository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<Instructor> findByEmail(String email) {
        return repository.findByEmail(email)
                .map(this::toDomain);
    }

    @Override
    public PageResult<Instructor> list(PageQuery pageQuery) {
        Page<InstructorJpaEntity> page = repository.findAll(toPageable(pageQuery));
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

    private Instructor toDomain(InstructorJpaEntity entity) {
        return new Instructor(entity.id(), entity.name(), entity.email(), entity.biography());
    }

    private PageRequest toPageable(PageQuery pageQuery) {
        String property = switch (pageQuery.sortField()) {
            case "name" -> "name";
            case "email" -> "email";
            default -> throw new IllegalArgumentException("Unsupported instructor sort field: " + pageQuery.sortField());
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
