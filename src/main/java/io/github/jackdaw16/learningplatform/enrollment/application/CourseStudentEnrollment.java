package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record CourseStudentEnrollment(
        UUID studentId,
        String firstName,
        String lastName,
        String email,
        UUID enrollmentId,
        EnrollmentStatus enrollmentStatus,
        int progress,
        Instant enrolledAt
) {
}
