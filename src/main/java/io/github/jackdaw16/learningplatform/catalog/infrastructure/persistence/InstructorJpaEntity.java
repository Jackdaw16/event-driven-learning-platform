package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "instructors")
public class InstructorJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "biography")
    private String biography;

    protected InstructorJpaEntity() {
    }

    InstructorJpaEntity(UUID id, String name, String email, String biography) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.biography = biography;
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    String email() {
        return email;
    }

    String biography() {
        return biography;
    }
}
