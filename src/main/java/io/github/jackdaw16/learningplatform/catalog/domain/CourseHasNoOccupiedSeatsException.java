package io.github.jackdaw16.learningplatform.catalog.domain;

public final class CourseHasNoOccupiedSeatsException extends IllegalStateException {

    public CourseHasNoOccupiedSeatsException() {
        super("Course has no occupied seats to release");
    }
}
