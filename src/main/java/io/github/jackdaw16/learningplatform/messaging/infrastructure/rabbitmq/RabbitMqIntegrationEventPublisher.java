package io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq;

import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.PendingOutboxEvent;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqIntegrationEventPublisher implements IntegrationEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitMqIntegrationEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${messaging.outbox.confirm-timeout:5s}") Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void publish(PendingOutboxEvent event) {
        CorrelationData correlationData = new CorrelationData(event.eventId().toString());
        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, event.routingKey(), messageFor(event), correlationData);

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ negatively acknowledged event " + event.eventId());
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned event " + event.eventId());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for RabbitMQ confirmation", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ did not confirm event " + event.eventId(), exception);
        }
    }

    private Message messageFor(PendingOutboxEvent event) {
        return MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.eventId().toString())
                .setHeader("eventType", event.type())
                .setHeader("eventVersion", event.version())
                .build();
    }
}
