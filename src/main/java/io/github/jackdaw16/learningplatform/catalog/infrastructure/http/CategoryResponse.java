package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.domain.Category;
import io.github.jackdaw16.learningplatform.catalog.domain.CategoryStatus;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description, CategoryStatus status) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.id(), category.name(), category.description(), category.status());
    }
}
