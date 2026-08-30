package io.github.jackdaw16.learningplatform.payment.infrastructure.simulation;

import io.github.jackdaw16.learningplatform.payment.application.port.PaymentOutcome;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentProcessor;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentProcessor implements PaymentProcessor {

    private final PaymentOutcome outcome;

    public SimulatedPaymentProcessor(
            @Value("${messaging.payment.simulated-outcome:CONFIRMED}") PaymentOutcome outcome
    ) {
        this.outcome = outcome;
    }

    @Override
    public PaymentOutcome process(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");
        return outcome;
    }
}
