package io.github.jackdaw16.learningplatform.enrollment.application;

import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record StudentCourseEnrollment(
        UUID courseId,
        String title,
        CourseLevel level,
        CourseStatus courseStatus,
        UUID enrollmentId,
        EnrollmentStatus enrollmentStatus,
        int progress,
        Instant enrolledAt,
        Instant completedAt
) {
}
