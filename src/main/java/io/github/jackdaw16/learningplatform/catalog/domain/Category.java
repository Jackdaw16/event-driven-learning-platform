package io.github.jackdaw16.learningplatform.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public final class Category {

    private final UUID id;
    private String name;
    private String description;
    private CategoryStatus status;

    public Category(UUID id, String name, String description) {
        this(id, name, description, CategoryStatus.ACTIVE);
    }

    private Category(UUID id, String name, String description, CategoryStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        validateName(name);
        this.name = name;
        this.description = description;
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static Category rehydrate(UUID id, String name, String description, CategoryStatus status) {
        return new Category(id, name, description, status);
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

    public void rename(String name) {
        validateName(name);
        this.name = name;
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void archive() {
        if (status == CategoryStatus.ARCHIVED) {
            throw new IllegalStateException("Category is already archived");
        }
        status = CategoryStatus.ARCHIVED;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
