package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.enrollment.application.CreateEnrollmentResult;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.payment.domain.PaymentStatus;
import java.util.UUID;

public record EnrollmentCreationResponse(
        UUID enrollmentId,
        UUID studentId,
        UUID courseId,
        EnrollmentStatus enrollmentStatus,
        UUID paymentId,
        PaymentStatus paymentStatus
) {

    public static EnrollmentCreationResponse from(CreateEnrollmentResult result) {
        return new EnrollmentCreationResponse(
                result.enrollment().id(),
                result.enrollment().studentId(),
                result.enrollment().courseId(),
                result.enrollment().status(),
                result.payment().id(),
                result.payment().status()
        );
    }
}
