package io.github.jackdaw16.learningplatform.student.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Student(UUID id, String firstName, String lastName, String email, Instant registrationTimestamp) {

    public Student {
        id = Objects.requireNonNull(id, "id must not be null");
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("first name must not be blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("last name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        registrationTimestamp = Objects.requireNonNull(registrationTimestamp, "registration timestamp must not be null");
    }
}
