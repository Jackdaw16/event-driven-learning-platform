package io.github.jackdaw16.learningplatform.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record Instructor(UUID id, String name, String email, String biography) {

    public Instructor {
        id = Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }
}
