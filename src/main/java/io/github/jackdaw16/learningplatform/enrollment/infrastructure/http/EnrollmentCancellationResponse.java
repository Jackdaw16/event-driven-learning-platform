package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.enrollment.domain.Enrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.util.UUID;

public record EnrollmentCancellationResponse(
        UUID enrollmentId,
        UUID studentId,
        UUID courseId,
        EnrollmentStatus enrollmentStatus
) {

    public static EnrollmentCancellationResponse from(Enrollment enrollment) {
        return new EnrollmentCancellationResponse(
                enrollment.id(),
                enrollment.studentId(),
                enrollment.courseId(),
                enrollment.status()
        );
    }
}
