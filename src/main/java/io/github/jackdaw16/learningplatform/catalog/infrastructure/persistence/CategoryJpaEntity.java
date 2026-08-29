package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.catalog.domain.CategoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CategoryStatus status;

    protected CategoryJpaEntity() {
    }

    CategoryJpaEntity(UUID id, String name, String description, CategoryStatus status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    CategoryStatus status() {
        return status;
    }
}
