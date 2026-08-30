package io.github.jackdaw16.learningplatform.payment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByEnrollmentId(UUID enrollmentId);

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
