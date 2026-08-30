package io.github.jackdaw16.learningplatform.enrollment.application.exception;

import io.github.jackdaw16.learningplatform.catalog.application.exception.ConflictException;
import java.util.UUID;

public final class CourseNotEnrollableException extends ConflictException {

    public CourseNotEnrollableException(UUID courseId) {
        super("Course with id " + courseId + " is not enrollable");
    }
}
