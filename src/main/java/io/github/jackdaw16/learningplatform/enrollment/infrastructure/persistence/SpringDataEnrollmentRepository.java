package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEnrollmentRepository extends JpaRepository<EnrollmentJpaEntity, UUID> {

    Optional<EnrollmentJpaEntity> findByStudentIdAndCourseIdAndStatusIn(
            UUID studentId,
            UUID courseId,
            Collection<EnrollmentStatus> statuses
    );
}
