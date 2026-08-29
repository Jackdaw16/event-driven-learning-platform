package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InstructorRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String biography
) {
}
