package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ResourceNotFoundException;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import io.github.jackdaw16.learningplatform.messaging.PaymentConfirmedEventV1;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentRepository;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import io.github.jackdaw16.learningplatform.payment.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentPaymentConfirmedService {

    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;

    public EnrollmentPaymentConfirmedService(
            EnrollmentRepository enrollmentRepository,
            PaymentRepository paymentRepository
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirm(PaymentConfirmedEventV1 event) {
        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", event.paymentId()));
        if (!payment.enrollmentId().equals(event.enrollmentId())) {
            throw new IllegalStateException("payment does not belong to the enrollment in the event");
        }
        if (payment.status() != PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("only confirmed payments can activate enrollments");
        }

        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(event.enrollmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", event.enrollmentId()));
        if (enrollment.status() != EnrollmentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("only pending payment enrollments can be activated");
        }
        enrollment.activate();
        enrollmentRepository.save(enrollment);
    }
}
