package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.enrollment.application.exception.EnrollmentAlreadyExistsException;
import io.github.jackdaw16.learningplatform.enrollment.application.exception.IdempotencyConflictException;
import io.github.jackdaw16.learningplatform.enrollment.application.port.EnrollmentRepository;
import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.payment.application.port.PaymentRepository;
import io.github.jackdaw16.learningplatform.payment.domain.Payment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentCreationService {

    private final EnrollmentCreationTransaction transaction;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;

    public EnrollmentCreationService(
            EnrollmentCreationTransaction transaction,
            EnrollmentRepository enrollmentRepository,
            PaymentRepository paymentRepository
    ) {
        this.transaction = transaction;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
    }

    public CreateEnrollmentResult create(CreateEnrollmentCommand command) {
        return paymentRepository.findByIdempotencyKey(command.idempotencyKey())
                .map(payment -> replayOrConflict(command, payment))
                .orElseGet(() -> createAfterMissingPayment(command));
    }

    private CreateEnrollmentResult createAfterMissingPayment(CreateEnrollmentCommand command) {
        try {
            return transaction.create(command);
        } catch (DataIntegrityViolationException exception) {
            return recoverAfterRollback(command, exception);
        }
    }

    private CreateEnrollmentResult recoverAfterRollback(
            CreateEnrollmentCommand command,
            DataIntegrityViolationException exception
    ) {
        Payment payment = paymentRepository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (payment != null) {
            return replayOrConflict(command, payment);
        }

        Enrollment enrollment = enrollmentRepository.findLiveByStudentIdAndCourseId(
                command.studentId(), command.courseId()
        ).orElse(null);
        if (enrollment != null) {
            throw new EnrollmentAlreadyExistsException(command.studentId(), command.courseId());
        }
        throw exception;
    }

    private CreateEnrollmentResult replayOrConflict(CreateEnrollmentCommand command, Payment payment) {
        Enrollment enrollment = enrollmentRepository.findById(payment.enrollmentId())
                .orElseThrow(() -> new IllegalStateException("Payment references a missing enrollment"));
        if (!enrollment.studentId().equals(command.studentId()) || !enrollment.courseId().equals(command.courseId())) {
            throw new IdempotencyConflictException(command.idempotencyKey());
        }
        return new CreateEnrollmentResult(enrollment, payment, true);
    }
}
