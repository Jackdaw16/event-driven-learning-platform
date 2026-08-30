package io.github.jackdaw16.learningplatform.messaging.application;

import java.util.UUID;

public record IncomingEventMetadata(UUID eventId, String eventType, int eventVersion) {
}
