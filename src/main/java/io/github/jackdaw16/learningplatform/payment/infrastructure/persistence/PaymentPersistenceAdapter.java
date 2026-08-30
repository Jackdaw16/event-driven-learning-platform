package io.github.jackdaw16.learningplatform.payment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.payment.application.port.PaymentRepository;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import io.github.jackdaw16.learningplatform.shared.Money;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final SpringDataPaymentRepository repository;

    public PaymentPersistenceAdapter(SpringDataPaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(Payment payment) {
        repository.save(new PaymentJpaEntity(
                payment.id(),
                payment.enrollmentId(),
                payment.amount().amount(),
                payment.amount().currency().getCurrencyCode(),
                payment.status(),
                payment.idempotencyKey(),
                payment.createdAt()
        ));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByEnrollmentId(UUID enrollmentId) {
        return repository.findByEnrollmentId(enrollmentId).map(this::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    private Payment toDomain(PaymentJpaEntity entity) {
        return Payment.rehydrate(
                entity.id(),
                entity.enrollmentId(),
                new Money(entity.amount(), Currency.getInstance(entity.currencyCode())),
                entity.idempotencyKey(),
                entity.createdAt(),
                entity.status()
        );
    }
}
