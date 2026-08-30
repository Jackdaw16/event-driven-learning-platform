package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentProgressResponse(
        UUID enrollmentId,
        EnrollmentStatus status,
        int progress,
        Instant completedAt
) {

    public static EnrollmentProgressResponse from(Enrollment enrollment) {
        return new EnrollmentProgressResponse(
                enrollment.id(),
                enrollment.status(),
                enrollment.progress(),
                enrollment.completedAt()
        );
    }
}
