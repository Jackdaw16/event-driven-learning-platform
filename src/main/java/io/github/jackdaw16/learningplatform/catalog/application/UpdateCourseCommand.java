package io.github.jackdaw16.learningplatform.catalog.application;

import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.shared.Money;
import java.util.UUID;

public record UpdateCourseCommand(
        String title,
        String description,
        int estimatedDurationHours,
        CourseLevel level,
        Money price,
        int maximumSeats,
        UUID categoryId,
        UUID instructorId
) {
}
