package io.github.jackdaw16.learningplatform.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventContractsV1Test {

    @Test
    void contractsCarryStableV1Metadata() {
        var metadata = new EventMetadataV1(UUID.randomUUID(), EnrollmentCreatedEventV1.EVENT_TYPE,
                EnrollmentCreatedEventV1.VERSION, Instant.now());
        var event = new EnrollmentCreatedEventV1(metadata, UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, "USD");

        assertEquals(metadata, event.metadata());
        assertEquals("enrollment.created.v1", EnrollmentCreatedEventV1.EVENT_TYPE);
        assertEquals("payment.confirmed.v1", PaymentConfirmedEventV1.EVENT_TYPE);
        assertEquals("payment.failed.v1", PaymentFailedEventV1.EVENT_TYPE);
        assertEquals("enrollment.completed.v1", EnrollmentCompletedEventV1.EVENT_TYPE);
        assertTrue(List.of(EnrollmentCreatedEventV1.VERSION, PaymentConfirmedEventV1.VERSION,
                PaymentFailedEventV1.VERSION, EnrollmentCompletedEventV1.VERSION).stream().allMatch(version -> version == 1));
    }

    @Test
    void contractsOnlyReferenceTransportAndJdkValueTypes() {
        for (Class<?> contract : List.of(EnrollmentCreatedEventV1.class, PaymentConfirmedEventV1.class,
                PaymentFailedEventV1.class, EnrollmentCompletedEventV1.class)) {
            assertTrue(contract.isRecord());
            var components = List.of(contract.getRecordComponents());
            assertTrue(components.stream().anyMatch(component -> component.getType() == EventMetadataV1.class));
            assertTrue(components.stream()
                    .map(RecordComponent::getType)
                    .allMatch(this::isAllowedTransportType));
        }
    }

    private boolean isAllowedTransportType(Class<?> type) {
        return type == EventMetadataV1.class || type == UUID.class || type == String.class
                || type == BigDecimal.class || type == Instant.class;
    }
}
