package io.github.jackdaw16.learningplatform.auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAuthUserRepository extends JpaRepository<AuthUserJpaEntity, UUID> {

    Optional<AuthUserJpaEntity> findByUsername(String username);
}
