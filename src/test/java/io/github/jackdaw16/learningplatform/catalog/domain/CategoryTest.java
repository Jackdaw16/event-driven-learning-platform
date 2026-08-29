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
}
