package io.github.jackdaw16.learningplatform.messaging.application;

import java.util.UUID;

public record ProcessedEvent(UUID eventId, String eventType) {
}
