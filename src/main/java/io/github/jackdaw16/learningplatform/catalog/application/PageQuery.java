package io.github.jackdaw16.learningplatform.catalog.application;

import java.util.Objects;

public record PageQuery(int page, int size, String sortField, SortDirection sortDirection) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (sortField == null || sortField.isBlank()) {
            throw new IllegalArgumentException("sort field must not be blank");
        }
        Objects.requireNonNull(sortDirection, "sort direction must not be null");
    }
}
