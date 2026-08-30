package io.github.jackdaw16.learningplatform.auth.infrastructure.http;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
