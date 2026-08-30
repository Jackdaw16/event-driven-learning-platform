package io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq;

import io.github.jackdaw16.learningplatform.messaging.EventMetadataV1;
import io.github.jackdaw16.learningplatform.messaging.IntegrationEvent;
import io.github.jackdaw16.learningplatform.messaging.application.IncomingEventMetadata;
import java.util.Objects;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class IntegrationEventMessageValidator {

    private final ObjectMapper objectMapper;

    public IntegrationEventMessageValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T extends IntegrationEvent> T deserializeAndValidate(
            Message message,
            IncomingEventMetadata incoming,
            Class<T> payloadType,
            String expectedEventType,
            int expectedEventVersion
    ) {
        T payload = deserialize(message, payloadType);
        EventMetadataV1 metadata = Objects.requireNonNull(payload.metadata(), "payload metadata must not be null");
        if (!expectedEventType.equals(metadata.eventType()) || metadata.version() != expectedEventVersion) {
            throw new IllegalArgumentException("payload does not match the listener contract");
        }
        if (!metadata.eventId().equals(incoming.eventId())
                || !metadata.eventType().equals(incoming.eventType())
                || metadata.version() != incoming.eventVersion()) {
            throw new IllegalArgumentException("payload metadata does not match AMQP metadata");
        }
        return payload;
    }

    private <T extends IntegrationEvent> T deserialize(Message message, Class<T> payloadType) {
        try {
            return objectMapper.readValue(message.getBody(), payloadType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("message payload cannot be deserialized", exception);
        }
    }
}
