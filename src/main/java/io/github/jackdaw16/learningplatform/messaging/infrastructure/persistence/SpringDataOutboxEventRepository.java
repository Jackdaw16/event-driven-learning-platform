package io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
}
