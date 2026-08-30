package io.github.jackdaw16.learningplatform.messaging.application;

import io.github.jackdaw16.learningplatform.messaging.application.port.ProcessedEventStore;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class IdempotentEventProcessor {

    private final ProcessedEventStore processedEventStore;
    private final TransactionTemplate transactionTemplate;

    public IdempotentEventProcessor(ProcessedEventStore processedEventStore, TransactionTemplate transactionTemplate) {
        this.processedEventStore = processedEventStore;
        this.transactionTemplate = transactionTemplate;
    }

    public void process(IncomingEventMetadata event, String expectedEventType, int expectedEventVersion, Runnable effect) {
        validate(event, expectedEventType, expectedEventVersion);
        Objects.requireNonNull(effect, "effect must not be null");

        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (processedEventStore.isProcessed(event.eventId())) {
                    throw new AlreadyProcessed();
                }

                effect.run();
                if (!processedEventStore.recordIfUnprocessed(new ProcessedEvent(event.eventId(), event.eventType()))) {
                    // A concurrent transaction committed first, so its loser must not retain its effect.
                    throw new AlreadyProcessed();
                }
            });
        } catch (AlreadyProcessed ignored) {
            // A duplicate is acknowledged normally after its transaction is rolled back.
        }
    }

    private void validate(IncomingEventMetadata event, String expectedEventType, int expectedEventVersion) {
        Objects.requireNonNull(event, "event must not be null");
        if (expectedEventType == null || expectedEventType.isBlank() || expectedEventVersion < 1) {
            throw new IllegalArgumentException("expected event contract must be usable");
        }

        if (!expectedEventType.equals(event.eventType())) {
            throw new IllegalArgumentException("eventType header does not match the listener contract");
        }

        if (event.eventVersion() != expectedEventVersion) {
            throw new IllegalArgumentException("eventVersion header does not match the listener contract");
        }
    }

    private static final class AlreadyProcessed extends RuntimeException {
    }
}
