package io.github.jackdaw16.learningplatform.payment.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCreatedEventV1;
import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.PaymentConfirmedEventV1;
import io.github.jackdaw16.learningplatform.messaging.PaymentFailedEventV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventRecorder;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentOutcome;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentProcessor;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentRepository;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import io.github.jackdaw16.learningplatform.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentProcessingService {

    private final PaymentRepository paymentRepository;
    private final PaymentProcessor paymentProcessor;
    private final IntegrationEventRecorder integrationEventRecorder;
    private final Clock clock;

    public PaymentProcessingService(
            PaymentRepository paymentRepository,
            PaymentProcessor paymentProcessor,
            IntegrationEventRecorder integrationEventRecorder,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProcessor = paymentProcessor;
        this.integrationEventRecorder = integrationEventRecorder;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void process(EnrollmentCreatedEventV1 event) {
        Payment payment = paymentRepository.findByIdForUpdate(event.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", event.paymentId()));
        if (!payment.enrollmentId().equals(event.enrollmentId())) {
            throw new IllegalArgumentException("payment does not belong to the enrollment in the event");
        }
        if (payment.status() != PaymentStatus.PENDING) {
            throw new IllegalStateException("only pending payments can be processed");
        }

        Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        switch (paymentProcessor.process(payment)) {
            case CONFIRMED -> confirm(payment, occurredAt);
            case FAILED -> fail(payment, occurredAt);
        }
    }

    private void confirm(Payment payment, Instant occurredAt) {
        payment.confirm();
        paymentRepository.save(payment);
        integrationEventRecorder.record(new PaymentConfirmedEventV1(
                metadata(PaymentConfirmedEventV1.EVENT_TYPE, occurredAt), payment.id(), payment.enrollmentId()
        ), RabbitTopology.PAYMENT_CONFIRMED_ROUTING_KEY);
    }

    private void fail(Payment payment, Instant occurredAt) {
        payment.fail();
        paymentRepository.save(payment);
        integrationEventRecorder.record(new PaymentFailedEventV1(
                metadata(PaymentFailedEventV1.EVENT_TYPE, occurredAt), payment.id(), payment.enrollmentId()
        ), RabbitTopology.PAYMENT_FAILED_ROUTING_KEY);
    }

    private EventMetadataV1 metadata(String eventType, Instant occurredAt) {
        return new EventMetadataV1(UUID.randomUUID(), eventType, 1, occurredAt);
    }
}
