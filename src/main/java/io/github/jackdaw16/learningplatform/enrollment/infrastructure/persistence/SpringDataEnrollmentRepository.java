package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.enrollment.domain.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEnrollmentRepository extends JpaRepository<EnrollmentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT enrollment FROM EnrollmentJpaEntity enrollment WHERE enrollment.id = :enrollmentId")
    Optional<EnrollmentJpaEntity> findByIdForUpdate(@Param("enrollmentId") UUID enrollmentId);

    Optional<EnrollmentJpaEntity> findByStudentIdAndCourseIdAndStatusIn(
            UUID studentId,
            UUID courseId,
            Collection<EnrollmentStatus> statuses
    );
}
