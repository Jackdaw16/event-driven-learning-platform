package io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq;

import io.github.jackdaw16.learningplatform.messaging.application.IncomingEventMetadata;
import java.util.Objects;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

@Component
public class IncomingEventMetadataExtractor {

    public IncomingEventMetadata extract(Message message) {
        Objects.requireNonNull(message, "message must not be null");

        MessageProperties properties = message.getMessageProperties();
        UUID eventId = eventId(properties.getMessageId());
        String eventType = eventType(properties.getHeader("eventType"));
        int eventVersion = eventVersion(properties.getHeader("eventVersion"));
        return new IncomingEventMetadata(eventId, eventType, eventVersion);
    }

    private UUID eventId(String messageId) {
        try {
            return UUID.fromString(messageId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("messageId must contain a UUID event id", exception);
        }
    }

    private String eventType(Object eventTypeHeader) {
        if (eventTypeHeader instanceof String eventType) {
            return eventType;
        }
        throw new IllegalArgumentException("eventType header does not match the listener contract");
    }

    private int eventVersion(Object eventVersionHeader) {
        if (!(eventVersionHeader instanceof Number eventVersion)
                || eventVersion.doubleValue() != eventVersion.longValue()
                || eventVersion.longValue() < Integer.MIN_VALUE
                || eventVersion.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("eventVersion header does not match the listener contract");
        }
        return (int) eventVersion.longValue();
    }
}
