package io.github.jackdaw16.learningplatform.payment.application.port;

import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    void save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdForUpdate(UUID id);

    Optional<Payment> findByEnrollmentId(UUID enrollmentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
