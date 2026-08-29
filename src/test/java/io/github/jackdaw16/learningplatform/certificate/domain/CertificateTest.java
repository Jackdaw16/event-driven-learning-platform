package io.github.jackdaw16.learningplatform.certificate.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CertificateTest {

    @Test
    void rejectsMissingIdEnrollmentReferenceVerificationCodeAndIssuanceTimestamp() {
        Instant issuedAt = Instant.parse("2026-08-29T12:00:00Z");

        assertThrows(NullPointerException.class, () -> new Certificate(null, UUID.randomUUID(), "certificate-1", issuedAt));
        assertThrows(NullPointerException.class, () -> new Certificate(UUID.randomUUID(), null, "certificate-1", issuedAt));
        assertThrows(IllegalArgumentException.class, () -> new Certificate(UUID.randomUUID(), UUID.randomUUID(), null, issuedAt));
        assertThrows(IllegalArgumentException.class, () -> new Certificate(UUID.randomUUID(), UUID.randomUUID(), "  ", issuedAt));
        assertThrows(NullPointerException.class, () -> new Certificate(UUID.randomUUID(), UUID.randomUUID(), "certificate-1", null));
    }
}
