package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EnrollmentProgressRequest(
        @NotNull @Min(0) @Max(100) Integer progress
) {
}
