package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import java.util.Objects;

public record CreateEnrollmentResult(Enrollment enrollment, Payment payment, boolean replayed) {

    public CreateEnrollmentResult {
        enrollment = Objects.requireNonNull(enrollment, "enrollment must not be null");
        payment = Objects.requireNonNull(payment, "payment must not be null");
    }
}
