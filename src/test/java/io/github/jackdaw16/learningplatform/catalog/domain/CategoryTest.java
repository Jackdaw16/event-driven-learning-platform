package io.github.jackdaw16.learningplatform.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void startsActiveAndCanBeArchivedExplicitly() {
        Category category = new Category(UUID.randomUUID(), "Programming", "Software development courses");

        assertEquals(CategoryStatus.ACTIVE, category.status());

        category.archive();

        assertEquals(CategoryStatus.ARCHIVED, category.status());
        assertThrows(IllegalStateException.class, category::archive);
    }

    @Test
    void rejectsBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> new Category(UUID.randomUUID(), "  ", "Description"));
    }

    @Test
    void rehydratesActiveAndArchivedCategories() {
        Category active = Category.rehydrate(UUID.randomUUID(), "Programming", "Software development courses", CategoryStatus.ACTIVE);
        Category archived = Category.rehydrate(UUID.randomUUID(), "Design", "Design courses", CategoryStatus.ARCHIVED);

        assertEquals(CategoryStatus.ACTIVE, active.status());
        assertEquals(CategoryStatus.ARCHIVED, archived.status());
    }

    @Test
    void rejectsNullStatusDuringRehydration() {
        assertThrows(
                NullPointerException.class,
                () -> Category.rehydrate(UUID.randomUUID(), "Programming", "Software development courses", null)
        );
    }

    @Test
    void renamesUsingTheSameNameValidationAsConstruction() {
        Category category = new Category(UUID.randomUUID(), "Programming", "Software development courses");

        category.rename("Development");

        assertEquals("Development", category.name());
        assertThrows(IllegalArgumentException.class, () -> category.rename("  "));
    }
}
