package io.github.jackdaw16.learningplatform.messaging.application.port;

import io.github.jackdaw16.learningplatform.messaging.application.PendingOutboxEvent;

public interface IntegrationEventPublisher {

    void publish(PendingOutboxEvent event);
}
