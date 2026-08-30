package io.github.jackdaw16.learningplatform.enrollment.infrastructure.http;

import io.github.jackdaw16.learningplatform.enrollment.application.CourseStudentEnrollment;
import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record CourseStudentEnrollmentResponse(
        UUID studentId,
        String firstName,
        String lastName,
        String email,
        UUID enrollmentId,
        EnrollmentStatus enrollmentStatus,
        int progress,
        Instant enrolledAt
) {

    static CourseStudentEnrollmentResponse from(CourseStudentEnrollment enrollment) {
        return new CourseStudentEnrollmentResponse(
                enrollment.studentId(),
                enrollment.firstName(),
                enrollment.lastName(),
                enrollment.email(),
                enrollment.enrollmentId(),
                enrollment.enrollmentStatus(),
                enrollment.progress(),
                enrollment.enrolledAt()
        );
    }
}
