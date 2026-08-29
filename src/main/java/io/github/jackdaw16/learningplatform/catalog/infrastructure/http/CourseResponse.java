package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.domain.Course;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        String title,
        String description,
        int estimatedDurationHours,
        CourseLevel level,
        BigDecimal priceAmount,
        String currency,
        int maximumSeats,
        int occupiedSeats,
        CourseStatus status,
        UUID categoryId,
        UUID instructorId
) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.id(),
                course.title(),
                course.description(),
                course.estimatedDurationHours(),
                course.level(),
                course.price().amount(),
                course.price().currency().getCurrencyCode(),
                course.maximumSeats(),
                course.occupiedSeats(),
                course.status(),
                course.categoryId(),
                course.instructorId()
        );
    }
}
