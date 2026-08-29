package io.github.jackdaw16.learningplatform.catalog.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInstructorRepository extends JpaRepository<InstructorJpaEntity, UUID> {

    Optional<InstructorJpaEntity> findByEmail(String email);
}
