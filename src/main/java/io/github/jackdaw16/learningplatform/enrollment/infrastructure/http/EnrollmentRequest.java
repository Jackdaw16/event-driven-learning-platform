package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EnrollmentRequest(@NotNull UUID courseId) {
}
