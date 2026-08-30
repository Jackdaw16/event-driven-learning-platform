package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.catalog.domain.CourseLevel;
import io.github.jackdaw16.learningplatform.catalog.domain.CourseStatus;
import io.github.jackdaw16.learningplatform.enrollment.application.StudentCourseEnrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record StudentCourseEnrollmentResponse(
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

    static StudentCourseEnrollmentResponse from(StudentCourseEnrollment enrollment) {
        return new StudentCourseEnrollmentResponse(
                enrollment.courseId(),
                enrollment.title(),
                enrollment.level(),
                enrollment.courseStatus(),
                enrollment.enrollmentId(),
                enrollment.enrollmentStatus(),
                enrollment.progress(),
                enrollment.enrolledAt(),
                enrollment.completedAt()
        );
    }
}
