package io.github.jackdaw16.learningplatform.catalog.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void rejectsInvalidPageAndSizeBounds() {
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(-1, 10, "name", SortDirection.ASC));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 0, "name", SortDirection.ASC));
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, 101, "name", SortDirection.ASC));
    }

    @Test
    void acceptsTheSupportedPaginationRange() {
        assertDoesNotThrow(() -> new PageQuery(0, 1, "name", SortDirection.ASC));
        assertDoesNotThrow(() -> new PageQuery(2, 100, "name", SortDirection.DESC));
    }
}
