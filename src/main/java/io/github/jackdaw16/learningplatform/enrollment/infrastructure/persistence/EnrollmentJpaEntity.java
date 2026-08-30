package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
public class EnrollmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EnrollmentStatus status;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected EnrollmentJpaEntity() {
    }

    EnrollmentJpaEntity(
            UUID id,
            UUID studentId,
            UUID courseId,
            EnrollmentStatus status,
            int progress,
            Instant enrolledAt,
            Instant completedAt
    ) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.status = status;
        this.progress = progress;
        this.enrolledAt = enrolledAt;
        this.completedAt = completedAt;
    }

    UUID id() {
        return id;
    }

    UUID studentId() {
        return studentId;
    }

    UUID courseId() {
        return courseId;
    }

    EnrollmentStatus status() {
        return status;
    }

    int progress() {
        return progress;
    }

    Instant enrolledAt() {
        return enrolledAt;
    }

    Instant completedAt() {
        return completedAt;
    }
}
