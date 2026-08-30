package io.github.jackdaw16.learningplatform.messaging.application.port;

import io.github.jackdaw16.learningplatform.messaging.application.ProcessedEvent;
import java.util.UUID;

public interface ProcessedEventStore {

    boolean isProcessed(UUID eventId);

    boolean recordIfUnprocessed(ProcessedEvent event);
}
