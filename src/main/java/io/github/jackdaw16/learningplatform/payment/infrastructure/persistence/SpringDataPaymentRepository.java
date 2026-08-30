package io.github.jackdaw16.learningplatform.payment.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM PaymentJpaEntity payment WHERE payment.id = :paymentId")
    Optional<PaymentJpaEntity> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    Optional<PaymentJpaEntity> findByEnrollmentId(UUID enrollmentId);

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
