package io.github.jackdaw16.learningplatform.enrollment.application;

import java.util.List;
import java.util.Objects;

public record RelationshipPageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public RelationshipPageResult {
        content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
        if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("invalid relationship page result");
        }
    }
}
