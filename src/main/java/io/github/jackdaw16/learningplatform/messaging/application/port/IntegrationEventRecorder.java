package io.github.jackdaw16.learningplatform.messaging.application.port;

import io.github.jackdaw16.learningplatform.messaging.IntegrationEvent;

public interface IntegrationEventRecorder {

    void record(IntegrationEvent event, String routingKey);
}
