package io.github.jackdaw16.learningplatform.catalog.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    public CategoryService(CategoryRepository categoryRepository, CourseRepository courseRepository) {
        this.categoryRepository = categoryRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Category create(String name, String description) {
        if (categoryRepository.findByName(name).isPresent()) {
            throw new ConflictException("Category name already exists");
        }

        Category category = new Category(UUID.randomUUID(), name, description);
        categoryRepository.save(category);
        return category;
    }

    @Transactional(readOnly = true)
    public Category getById(UUID id) {
        return findCategory(id);
    }

    @Transactional(readOnly = true)
    public PageResult<Category> list(PageQuery pageQuery) {
        validateSort(pageQuery);
        return categoryRepository.list(pageQuery);
    }

    @Transactional
    public Category update(UUID id, String name, String description) {
        Category category = findCategory(id);
        categoryRepository.findByName(name)
                .filter(existing -> !existing.id().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Category name already exists");
                });

        category.rename(name);
        category.changeDescription(description);
        categoryRepository.save(category);
        return category;
    }

    @Transactional
    public Category archive(UUID id) {
        Category category = findCategory(id);
        category.archive();
        categoryRepository.save(category);
        return category;
    }

    @Transactional
    public void delete(UUID id) {
        findCategory(id);
        if (courseRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category is referenced by courses");
        }
        categoryRepository.deleteById(id);
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    private void validateSort(PageQuery pageQuery) {
        if (!"name".equals(pageQuery.sortField())) {
            throw new IllegalArgumentException("Unsupported category sort field: " + pageQuery.sortField());
        }
    }
}
