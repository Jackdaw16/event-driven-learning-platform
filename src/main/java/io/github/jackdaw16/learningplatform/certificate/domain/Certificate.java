package io.github.jackdaw16.learningplatform.certificate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Certificate(UUID id, UUID enrollmentId, String verificationCode, Instant issuedAt) {

    public Certificate {
        id = Objects.requireNonNull(id, "id must not be null");
        enrollmentId = Objects.requireNonNull(enrollmentId, "enrollment id must not be null");
        if (verificationCode == null || verificationCode.isBlank()) {
            throw new IllegalArgumentException("verification code must not be blank");
        }
        issuedAt = Objects.requireNonNull(issuedAt, "issued at must not be null");
    }
}
