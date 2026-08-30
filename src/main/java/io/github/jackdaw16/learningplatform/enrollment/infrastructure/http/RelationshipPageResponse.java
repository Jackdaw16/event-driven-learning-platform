package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.enrollment.application.RelationshipPageResult;
import java.util.List;
import java.util.function.Function;

public record RelationshipPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <T, R> RelationshipPageResponse<R> from(RelationshipPageResult<T> page, Function<T, R> mapper) {
        return new RelationshipPageResponse<>(
                page.content().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
