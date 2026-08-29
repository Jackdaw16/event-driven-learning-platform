package io.github.jackdaw16.learningplatform.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public final class Category {

    private final UUID id;
    private final String name;
    private final String description;
    private CategoryStatus status;

    public Category(UUID id, String name, String description) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        this.description = description;
        this.status = CategoryStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public CategoryStatus status() {
        return status;
    }

    public void archive() {
        if (status == CategoryStatus.ARCHIVED) {
            throw new IllegalStateException("Category is already archived");
        }
        status = CategoryStatus.ARCHIVED;
    }
}
