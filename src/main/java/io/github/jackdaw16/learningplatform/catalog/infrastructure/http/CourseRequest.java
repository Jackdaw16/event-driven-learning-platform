package io.github.jackdaw16.learningplatform.catalog.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CourseRequest(
        @NotBlank String title,
        String description,
        @Positive int estimatedDurationHours,
        @NotNull CourseLevel level,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceAmount,
        @NotBlank String currency,
        @Positive int maximumSeats,
        @NotNull UUID categoryId,
        @NotNull UUID instructorId
) {
}
