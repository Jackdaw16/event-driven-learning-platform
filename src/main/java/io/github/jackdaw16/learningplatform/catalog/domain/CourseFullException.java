package io.github.jackdaw16.learningplatform.catalog.domain;

public final class CourseFullException extends IllegalStateException {

    public CourseFullException() {
        super("Course has reached its maximum number of seats");
    }
}
