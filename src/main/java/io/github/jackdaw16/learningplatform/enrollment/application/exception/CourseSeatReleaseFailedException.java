package io.github.jackdaw16.learningplatform.enrollment.application.exception;

import java.util.UUID;

public class CourseSeatReleaseFailedException extends IllegalStateException {

    public CourseSeatReleaseFailedException(UUID courseId) {
        super("Unable to release a seat for course " + courseId);
    }
}
