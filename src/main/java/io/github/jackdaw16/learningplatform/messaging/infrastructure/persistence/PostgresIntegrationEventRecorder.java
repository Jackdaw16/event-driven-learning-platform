package io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.IntegrationEvent;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventRecorder;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PostgresIntegrationEventRecorder implements IntegrationEventRecorder {

    private static final String PENDING_STATUS = "PENDING";

    private final SpringDataOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public PostgresIntegrationEventRecorder(
            SpringDataOutboxEventRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(IntegrationEvent event, String routingKey) {
        Objects.requireNonNull(event, "event must not be null");
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("routing key must not be blank");
        }

        EventMetadataV1 metadata = Objects.requireNonNull(event.metadata(), "event metadata must not be null");
        try {
            repository.save(new OutboxEventJpaEntity(
                    metadata.eventId(),
                    metadata.eventType(),
                    metadata.version(),
                    metadata.occurredAt(),
                    routingKey,
                    objectMapper.writeValueAsString(event),
                    PENDING_STATUS,
                    metadata.occurredAt(),
                    null,
                    0
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize integration event", exception);
        }
    }
}
