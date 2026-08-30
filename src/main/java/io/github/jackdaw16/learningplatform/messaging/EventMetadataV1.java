package io.github.jackdaw16.learningplatform.messaging;

import java.time.Instant;
import java.util.UUID;

public record EventMetadataV1(UUID eventId, String eventType, int version, Instant occurredAt) {
}
