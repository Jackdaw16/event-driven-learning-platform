package io.github.jackdaw16.learningplatform.enrollment.application.exception;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import java.util.UUID;

public final class EnrollmentCancellationNotAllowedException extends ConflictException {

    public EnrollmentCancellationNotAllowedException(UUID enrollmentId) {
        super("Enrollment with id " + enrollmentId + " cannot be cancelled");
    }
}
