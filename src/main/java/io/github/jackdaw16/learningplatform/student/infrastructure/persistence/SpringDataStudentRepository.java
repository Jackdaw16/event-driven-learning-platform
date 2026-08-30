package io.github.jackdaw16.learningplatform.student.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataStudentRepository extends JpaRepository<StudentJpaEntity, UUID> {
}
