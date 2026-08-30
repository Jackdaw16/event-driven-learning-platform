package io.github.jackdaw16.learningplatform.enrollment.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEnrollmentRepository extends JpaRepository<EnrollmentJpaEntity, UUID> {
}
