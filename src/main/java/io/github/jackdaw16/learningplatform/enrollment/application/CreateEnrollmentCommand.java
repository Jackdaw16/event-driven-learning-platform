package io.github.jackdaw16.learningplatform.enrollment.application;

import java.util.Objects;
import java.util.UUID;

public record CreateEnrollmentCommand(UUID studentId, UUID courseId, String idempotencyKey) {

    public CreateEnrollmentCommand {
        studentId = Objects.requireNonNull(studentId, "student id must not be null");
        courseId = Objects.requireNonNull(courseId, "course id must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (idempotencyKey.length() > 255) {
            throw new IllegalArgumentException("idempotency key must not exceed 255 characters");
        }
    }
}
