package io.github.jackdaw16.learningplatform.payment.application.port;

import io.github.jackdaw16.learningplatform.payment.domain.Payment;

public interface PaymentProcessor {

    PaymentOutcome process(Payment payment);
}
