package io.github.jackdaw16.learningplatform.certificate.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certificates")
public class CertificateJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, unique = true)
    private UUID enrollmentId;

    @Column(name = "verification_code", nullable = false, unique = true)
    private String verificationCode;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected CertificateJpaEntity() {
    }

    CertificateJpaEntity(UUID id, UUID enrollmentId, String verificationCode, Instant issuedAt) {
        this.id = id;
        this.enrollmentId = enrollmentId;
        this.verificationCode = verificationCode;
        this.issuedAt = issuedAt;
    }

    UUID id() {
        return id;
    }

    UUID enrollmentId() {
        return enrollmentId;
    }

    String verificationCode() {
        return verificationCode;
    }

    Instant issuedAt() {
        return issuedAt;
    }
}
