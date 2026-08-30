package io.github.jackdaw16.learningplatform.messaging;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record EnrollmentCreatedEventV1(
        EventMetadataV1 metadata,
        UUID enrollmentId,
        UUID paymentId,
        BigDecimal amount,
        String currency) implements IntegrationEvent {

    public static final String EVENT_TYPE = "enrollment.created.v1";
    public static final int VERSION = 1;

    public EnrollmentCreatedEventV1 {
        validateMetadata(metadata, EVENT_TYPE, VERSION);
    }

    private static void validateMetadata(EventMetadataV1 metadata, String eventType, int version) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        if (!eventType.equals(metadata.eventType()) || metadata.version() != version) {
            throw new IllegalArgumentException("metadata must use the event contract type and version");
        }
    }
}
