package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String name,
        String description
) {
}
