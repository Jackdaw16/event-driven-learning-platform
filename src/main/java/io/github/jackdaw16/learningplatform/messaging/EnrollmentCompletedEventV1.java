package io.github.jackdaw16.learningplatform.messaging;

import java.util.Objects;
import java.util.UUID;

public record EnrollmentCompletedEventV1(EventMetadataV1 metadata, UUID enrollmentId, UUID studentId, UUID courseId) implements IntegrationEvent {

    public static final String EVENT_TYPE = "enrollment.completed.v1";
    public static final int VERSION = 1;

    public EnrollmentCompletedEventV1 {
        validateMetadata(metadata, EVENT_TYPE, VERSION);
    }

    private static void validateMetadata(EventMetadataV1 metadata, String eventType, int version) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (!eventType.equals(metadata.eventType()) || metadata.version() != version) {
            throw new IllegalArgumentException("metadata must use the event contract type and version");
        }
    }
}
