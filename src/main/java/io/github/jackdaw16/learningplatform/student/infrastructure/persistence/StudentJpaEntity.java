package io.github.jackdaw16.learningplatform.student.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "students")
public class StudentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "registration_timestamp", nullable = false)
    private Instant registrationTimestamp;

    protected StudentJpaEntity() {
    }

    StudentJpaEntity(UUID id, String firstName, String lastName, String email, Instant registrationTimestamp) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.registrationTimestamp = registrationTimestamp;
    }

    UUID id() {
        return id;
    }

    String firstName() {
        return firstName;
    }

    String lastName() {
        return lastName;
    }

    String email() {
        return email;
    }

    Instant registrationTimestamp() {
        return registrationTimestamp;
    }
}
