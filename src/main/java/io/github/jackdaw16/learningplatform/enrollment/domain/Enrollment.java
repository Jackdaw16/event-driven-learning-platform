package io.github.jackdaw16.learningplatform.enrollment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Enrollment {

    private final UUID id;
    private final UUID studentId;
    private final UUID courseId;
    private final Instant enrolledAt;
    private EnrollmentStatus status;
    private int progress;
    private Instant completedAt;

    public Enrollment(UUID id, UUID studentId, UUID courseId, Instant enrolledAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.studentId = Objects.requireNonNull(studentId, "student id must not be null");
        this.courseId = Objects.requireNonNull(courseId, "course id must not be null");
        this.enrolledAt = Objects.requireNonNull(enrolledAt, "enrollment timestamp must not be null");
        this.status = EnrollmentStatus.PENDING_PAYMENT;
        this.progress = 0;
    }

    public static Enrollment rehydrate(
            UUID id,
            UUID studentId,
            UUID courseId,
            Instant enrolledAt,
            EnrollmentStatus status,
            int progress,
            Instant completedAt
    ) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(studentId, "student id must not be null");
        Objects.requireNonNull(courseId, "course id must not be null");
        Objects.requireNonNull(enrolledAt, "enrollment timestamp must not be null");
        Objects.requireNonNull(status, "status must not be null");
        validateRehydratedState(status, progress, completedAt);

        Enrollment enrollment = new Enrollment(id, studentId, courseId, enrolledAt);
        enrollment.status = status;
        enrollment.progress = progress;
        enrollment.completedAt = completedAt;
        return enrollment;
    }

    public UUID id() {
        return id;
    }

    public UUID studentId() {
        return studentId;
    }

    public UUID courseId() {
        return courseId;
    }

    public EnrollmentStatus status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    public Instant enrolledAt() {
        return enrolledAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public void activate() {
        if (status != EnrollmentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending payment enrollments can be activated");
        }
        status = EnrollmentStatus.ACTIVE;
    }

    public void updateProgress(int progress, Instant completedAt) {
        if (status != EnrollmentStatus.ACTIVE) {
            throw new IllegalStateException("Only active enrollments can update progress");
        }
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress must be between 0 and 100");
        }
        if (progress < 100 && completedAt != null) {
            throw new IllegalArgumentException("completion timestamp is only allowed at 100 progress");
        }
        if (progress == 100) {
            this.completedAt = Objects.requireNonNull(completedAt, "completion timestamp must not be null");
            this.progress = 100;
            this.status = EnrollmentStatus.COMPLETED;
            return;
        }
        this.progress = progress;
    }

    public void cancel() {
        if (status != EnrollmentStatus.PENDING_PAYMENT && status != EnrollmentStatus.ACTIVE) {
            throw new IllegalStateException("Only pending payment or active enrollments can be cancelled");
        }
        status = EnrollmentStatus.CANCELLED;
    }

    private static void validateRehydratedState(EnrollmentStatus status, int progress, Instant completedAt) {
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("progress must be between 0 and 100");
        }

        switch (status) {
            case PENDING_PAYMENT -> {
                if (progress != 0 || completedAt != null) {
                    throw new IllegalArgumentException("pending payment enrollments must have zero progress and no completion timestamp");
                }
            }
            case ACTIVE, CANCELLED -> {
                if (progress >= 100 || completedAt != null) {
                    throw new IllegalArgumentException("active and cancelled enrollments must be incomplete and have no completion timestamp");
                }
            }
            case COMPLETED -> {
                if (progress != 100 || completedAt == null) {
                    throw new IllegalArgumentException("completed enrollments must have full progress and a completion timestamp");
                }
            }
        }
    }
}
