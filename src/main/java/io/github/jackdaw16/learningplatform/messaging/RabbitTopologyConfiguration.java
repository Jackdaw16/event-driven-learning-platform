package io.github.jackdaw16.learningplatform.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfiguration {

    @Bean
    TopicExchange learningEventsExchange() {
        return new TopicExchange(RabbitTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange learningDeadLetterExchange() {
        return new TopicExchange(RabbitTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue paymentEnrollmentCreatedQueue() {
        return mainQueue(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE,
                RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLX_ROUTING_KEY);
    }

    @Bean
    Queue enrollmentPaymentConfirmedQueue() {
        return mainQueue(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_QUEUE,
                RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLX_ROUTING_KEY);
    }

    @Bean
    Queue enrollmentPaymentFailedQueue() {
        return mainQueue(RabbitTopology.ENROLLMENT_PAYMENT_FAILED_QUEUE,
                RabbitTopology.ENROLLMENT_PAYMENT_FAILED_DLX_ROUTING_KEY);
    }

    @Bean
    Queue certificateEnrollmentCompletedQueue() {
        return mainQueue(RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_QUEUE,
                RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_DLX_ROUTING_KEY);
    }

    @Bean
    Queue paymentEnrollmentCreatedDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ).build();
    }

    @Bean
    Queue enrollmentPaymentConfirmedDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLQ).build();
    }

    @Bean
    Queue enrollmentPaymentFailedDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.ENROLLMENT_PAYMENT_FAILED_DLQ).build();
    }

    @Bean
    Queue certificateEnrollmentCompletedDeadLetterQueue() {
        return QueueBuilder.durable(RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_DLQ).build();
    }

    @Bean
    Binding enrollmentCreatedBinding(TopicExchange learningEventsExchange, Queue paymentEnrollmentCreatedQueue) {
        return BindingBuilder.bind(paymentEnrollmentCreatedQueue).to(learningEventsExchange)
                .with(RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY);
    }

    @Bean
    Binding paymentConfirmedBinding(TopicExchange learningEventsExchange, Queue enrollmentPaymentConfirmedQueue) {
        return BindingBuilder.bind(enrollmentPaymentConfirmedQueue).to(learningEventsExchange)
                .with(RabbitTopology.PAYMENT_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    Binding paymentFailedBinding(TopicExchange learningEventsExchange, Queue enrollmentPaymentFailedQueue) {
        return BindingBuilder.bind(enrollmentPaymentFailedQueue).to(learningEventsExchange)
                .with(RabbitTopology.PAYMENT_FAILED_ROUTING_KEY);
    }

    @Bean
    Binding enrollmentCompletedBinding(TopicExchange learningEventsExchange, Queue certificateEnrollmentCompletedQueue) {
        return BindingBuilder.bind(certificateEnrollmentCompletedQueue).to(learningEventsExchange)
                .with(RabbitTopology.ENROLLMENT_COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding paymentEnrollmentCreatedDeadLetterBinding(
            TopicExchange learningDeadLetterExchange, Queue paymentEnrollmentCreatedDeadLetterQueue) {
        return BindingBuilder.bind(paymentEnrollmentCreatedDeadLetterQueue).to(learningDeadLetterExchange)
                .with(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLX_ROUTING_KEY);
    }

    @Bean
    Binding enrollmentPaymentConfirmedDeadLetterBinding(
            TopicExchange learningDeadLetterExchange, Queue enrollmentPaymentConfirmedDeadLetterQueue) {
        return BindingBuilder.bind(enrollmentPaymentConfirmedDeadLetterQueue).to(learningDeadLetterExchange)
                .with(RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_DLX_ROUTING_KEY);
    }

    @Bean
    Binding enrollmentPaymentFailedDeadLetterBinding(
            TopicExchange learningDeadLetterExchange, Queue enrollmentPaymentFailedDeadLetterQueue) {
        return BindingBuilder.bind(enrollmentPaymentFailedDeadLetterQueue).to(learningDeadLetterExchange)
                .with(RabbitTopology.ENROLLMENT_PAYMENT_FAILED_DLX_ROUTING_KEY);
    }

    @Bean
    Binding certificateEnrollmentCompletedDeadLetterBinding(
            TopicExchange learningDeadLetterExchange, Queue certificateEnrollmentCompletedDeadLetterQueue) {
        return BindingBuilder.bind(certificateEnrollmentCompletedDeadLetterQueue).to(learningDeadLetterExchange)
                .with(RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_DLX_ROUTING_KEY);
    }

    private Queue mainQueue(String name, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }
}
