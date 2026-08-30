package io.github.jackdaw16.learningplatform.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class RabbitTopologyConfigurationTest {

    @Test
    void declaresDurableExchangesQueuesAndBindingsInTheSpringContext() {
        try (var context = new AnnotationConfigApplicationContext(RabbitTopologyConfiguration.class)) {
            var exchanges = context.getBeansOfType(TopicExchange.class).values();
            var queues = context.getBeansOfType(Queue.class).values();
            var bindings = context.getBeansOfType(Binding.class).values();

            assertTrue(exchanges.stream().anyMatch(exchange -> exchange.getName().equals(RabbitTopology.EVENTS_EXCHANGE)
                    && exchange.isDurable()));
            assertTrue(exchanges.stream().anyMatch(exchange -> exchange.getName().equals(RabbitTopology.DEAD_LETTER_EXCHANGE)
                    && exchange.isDurable()));
            assertTrue(queues.stream().allMatch(Queue::isDurable));

            assertMainQueueDeadLettersTo(queues, RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE,
                    RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLX_ROUTING_KEY);
            assertMainQueueDeadLettersTo(queues, RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_QUEUE,
                    RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLX_ROUTING_KEY);
            assertMainQueueDeadLettersTo(queues, RabbitTopology.ENROLLMENT_PAYMENT_FAILED_QUEUE,
                    RabbitTopology.ENROLLMENT_PAYMENT_FAILED_DLX_ROUTING_KEY);
            assertMainQueueDeadLettersTo(queues, RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_QUEUE,
                    RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_DLX_ROUTING_KEY);

            assertBinding(bindings, RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY,
                    RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE);
            assertBinding(bindings, RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.PAYMENT_CONFIRMED_ROUTING_KEY,
                    RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_QUEUE);
            assertBinding(bindings, RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.PAYMENT_FAILED_ROUTING_KEY,
                    RabbitTopology.ENROLLMENT_PAYMENT_FAILED_QUEUE);
            assertBinding(bindings, RabbitTopology.EVENTS_EXCHANGE, RabbitTopology.ENROLLMENT_COMPLETED_ROUTING_KEY,
                    RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_QUEUE);
        }
    }

    private void assertMainQueueDeadLettersTo(Iterable<Queue> queues, String name, String routingKey) {
        var queue = findQueue(queues, name);
        Map<String, Object> arguments = queue.getArguments();
        assertEquals(RabbitTopology.DEAD_LETTER_EXCHANGE, arguments.get("x-dead-letter-exchange"));
        assertEquals(routingKey, arguments.get("x-dead-letter-routing-key"));
    }

    private void assertBinding(Iterable<Binding> bindings, String exchange, String routingKey, String queue) {
        assertTrue(((java.util.Collection<Binding>) bindings).stream()
                .anyMatch(binding -> binding.getExchange().equals(exchange)
                        && binding.getRoutingKey().equals(routingKey)
                        && binding.getDestination().equals(queue)));
    }

    private Queue findQueue(Iterable<Queue> queues, String name) {
        return ((java.util.Collection<Queue>) queues).stream()
                .filter(queue -> queue.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
