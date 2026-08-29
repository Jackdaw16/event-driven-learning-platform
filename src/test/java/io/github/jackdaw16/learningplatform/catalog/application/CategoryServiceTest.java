package io.github.jackdaw16.learningplatform.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.catalog.application.port.CategoryRepository;
import io.github.jackdaw16.learningplatform.catalog.application.port.CourseRepository;
import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.CategoryStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final CategoryService service = new CategoryService(categoryRepository, courseRepository);

    @Test
    void createsActiveCategoryAndSavesIt() {
        when(categoryRepository.findByName("Programming")).thenReturn(Optional.empty());

        Category created = service.create("Programming", "Software development courses");

        assertEquals("Programming", created.name());
        assertEquals("Software development courses", created.description());
        assertEquals(CategoryStatus.ACTIVE, created.status());
        verify(categoryRepository).save(created);
    }

    @Test
    void rejectsDuplicateCategoryNameDuringCreation() {
        when(categoryRepository.findByName("Programming"))
                .thenReturn(Optional.of(new Category(UUID.randomUUID(), "Programming", "Existing")));

        assertThrows(ConflictException.class, () -> service.create("Programming", "Another"));

        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updatesCategoryThroughDomainRevisionAndSavesIt() {
        Category category = new Category(UUID.randomUUID(), "Programming", "Existing description");
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));
        when(categoryRepository.findByName("Development")).thenReturn(Optional.empty());

        Category updated = service.update(category.id(), "Development", "Updated description");

        assertSame(category, updated);
        assertEquals("Development", updated.name());
        assertEquals("Updated description", updated.description());
        verify(categoryRepository).save(category);
    }

    @Test
    void permitsUpdatingCategoryWithItsOwnName() {
        Category category = new Category(UUID.randomUUID(), "Programming", "Existing description");
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));
        when(categoryRepository.findByName("Programming")).thenReturn(Optional.of(category));

        Category updated = service.update(category.id(), "Programming", "Updated description");

        assertEquals("Updated description", updated.description());
        verify(categoryRepository).save(category);
    }

    @Test
    void archivesCategoryThroughExplicitDomainTransition() {
        Category category = new Category(UUID.randomUUID(), "Programming", "Existing description");
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));

        Category archived = service.archive(category.id());

        assertEquals(CategoryStatus.ARCHIVED, archived.status());
        verify(categoryRepository).save(category);
    }

    @Test
    void deletesUnreferencedCategoryPhysically() {
        Category category = new Category(UUID.randomUUID(), "Programming", "Existing description");
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));
        when(courseRepository.existsByCategoryId(category.id())).thenReturn(false);

        service.delete(category.id());

        verify(categoryRepository).deleteById(category.id());
    }

    @Test
    void rejectsDeletingCategoryReferencedByCourses() {
        Category category = new Category(UUID.randomUUID(), "Programming", "Existing description");
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));
        when(courseRepository.existsByCategoryId(category.id())).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.delete(category.id()));

        verify(categoryRepository, never()).deleteById(category.id());
    }

    @Test
    void reportsMissingCategory() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getById(id));
    }
}
