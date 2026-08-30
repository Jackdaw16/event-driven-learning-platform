package io.github.jackdaw16.learningplatform.payment.domain;

import io.github.jackdaw16.learningplatform.shared.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Payment {

    private final UUID id;
    private final UUID enrollmentId;
    private final Money amount;
    private final String idempotencyKey;
    private final Instant createdAt;
    private PaymentStatus status;

    public Payment(UUID id, UUID enrollmentId, Money amount, String idempotencyKey, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollment id must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Objects.requireNonNull(createdAt, "creation timestamp must not be null");
        this.status = PaymentStatus.PENDING;
    }

    public static Payment rehydrate(
            UUID id,
            UUID enrollmentId,
            Money amount,
            String idempotencyKey,
            Instant createdAt,
            PaymentStatus status
    ) {
        Payment payment = new Payment(id, enrollmentId, amount, idempotencyKey, createdAt);
        payment.status = Objects.requireNonNull(status, "status must not be null");
        return payment;
    }

    public UUID id() {
        return id;
    }

    public UUID enrollmentId() {
        return enrollmentId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void confirm() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can be confirmed");
        }
        status = PaymentStatus.CONFIRMED;
    }

    public void fail() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can fail");
        }
        status = PaymentStatus.FAILED;
    }
}
