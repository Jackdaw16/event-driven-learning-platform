package io.github.jackdaw16.learningplatform.payment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentJpaEntity() {
    }

    PaymentJpaEntity(
            UUID id,
            UUID enrollmentId,
            BigDecimal amount,
            String currencyCode,
            PaymentStatus status,
            String idempotencyKey,
            Instant createdAt
    ) {
        this.id = id;
        this.enrollmentId = enrollmentId;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    UUID enrollmentId() {
        return enrollmentId;
    }

    BigDecimal amount() {
        return amount;
    }

    String currencyCode() {
        return currencyCode;
    }

    PaymentStatus status() {
        return status;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    Instant createdAt() {
        return createdAt;
    }
}
