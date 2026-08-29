package io.github.jackdaw16.learningplatform.payment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jackdaw16.learningplatform.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Money AMOUNT = new Money(new BigDecimal("49.99"), Currency.getInstance("USD"));

    @Test
    void startsPending() {
        Payment payment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), AMOUNT, "payment-1", Instant.parse("2026-08-29T12:00:00Z")
        );

        assertEquals(PaymentStatus.PENDING, payment.status());
    }

    @Test
    void requiresIdEnrollmentAmountIdempotencyKeyAndCreationTimestamp() {
        Instant createdAt = Instant.parse("2026-08-29T12:00:00Z");

        assertThrows(NullPointerException.class, () -> new Payment(null, UUID.randomUUID(), AMOUNT, "payment-1", createdAt));
        assertThrows(NullPointerException.class, () -> new Payment(UUID.randomUUID(), null, AMOUNT, "payment-1", createdAt));
        assertThrows(NullPointerException.class, () -> new Payment(UUID.randomUUID(), UUID.randomUUID(), null, "payment-1", createdAt));
        assertThrows(IllegalArgumentException.class, () -> new Payment(UUID.randomUUID(), UUID.randomUUID(), AMOUNT, null, createdAt));
        assertThrows(IllegalArgumentException.class, () -> new Payment(UUID.randomUUID(), UUID.randomUUID(), AMOUNT, "  ", createdAt));
        assertThrows(NullPointerException.class, () -> new Payment(UUID.randomUUID(), UUID.randomUUID(), AMOUNT, "payment-1", null));
    }

    @Test
    void confirmsOnlyPendingPayments() {
        Payment confirmedPayment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), AMOUNT, "payment-1", Instant.parse("2026-08-29T12:00:00Z")
        );
        confirmedPayment.confirm();

        assertEquals(PaymentStatus.CONFIRMED, confirmedPayment.status());
        assertThrows(IllegalStateException.class, confirmedPayment::confirm);

        Payment failedPayment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), AMOUNT, "payment-2", Instant.parse("2026-08-29T12:00:00Z")
        );
        failedPayment.fail();

        assertThrows(IllegalStateException.class, failedPayment::confirm);
    }

    @Test
    void failsOnlyPendingPayments() {
        Payment failedPayment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), AMOUNT, "payment-1", Instant.parse("2026-08-29T12:00:00Z")
        );
        failedPayment.fail();

        assertEquals(PaymentStatus.FAILED, failedPayment.status());
        assertThrows(IllegalStateException.class, failedPayment::fail);

        Payment confirmedPayment = new Payment(
                UUID.randomUUID(), UUID.randomUUID(), AMOUNT, "payment-2", Instant.parse("2026-08-29T12:00:00Z")
        );
        confirmedPayment.confirm();

        assertThrows(IllegalStateException.class, confirmedPayment::fail);
    }
}
