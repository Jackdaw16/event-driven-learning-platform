package io.github.jackdaw16.learningplatform.catalog.application;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("total elements must not be negative");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("total pages must not be negative");
        }
    }
}
