package io.github.jackdaw16.learningplatform.messaging.application;

import java.time.Instant;
import java.util.UUID;

public record PendingOutboxEvent(
        UUID eventId,
        String type,
        int version,
        Instant occurredAt,
        String routingKey,
        String payload,
        int attemptCount
) {
}
