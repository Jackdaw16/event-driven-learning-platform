package io.github.jackdaw16.learningplatform.enrollment.application.exception;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import java.util.UUID;

public final class EnrollmentAlreadyExistsException extends ConflictException {

    public EnrollmentAlreadyExistsException(UUID studentId, UUID courseId) {
        super("Student with id " + studentId + " already has a live enrollment for course with id " + courseId);
    }
}
