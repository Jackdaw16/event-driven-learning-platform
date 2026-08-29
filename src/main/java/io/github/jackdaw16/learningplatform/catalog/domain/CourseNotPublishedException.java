package io.github.jackdaw16.learningplatform.catalog.domain;

public final class CourseNotPublishedException extends IllegalStateException {

    public CourseNotPublishedException() {
        super("Only published courses can reserve seats");
    }
}
