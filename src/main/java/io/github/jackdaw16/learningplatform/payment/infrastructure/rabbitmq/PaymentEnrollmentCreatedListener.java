package io.github.jackdaw16.learningplatform.payment.infrastructure.rabbitmq;

import io.github.jackdaw16.learningplatform.messaging.EnrollmentCreatedEventV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.IdempotentEventProcessor;
import io.github.jackdaw16.learningplatform.messaging.application.IncomingEventMetadata;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IncomingEventMetadataExtractor;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IntegrationEventMessageValidator;
import io.github.jackdaw16.learningplatform.payment.application.PaymentProcessingService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEnrollmentCreatedListener {

    private final IncomingEventMetadataExtractor metadataExtractor;
    private final IntegrationEventMessageValidator messageValidator;
    private final IdempotentEventProcessor eventProcessor;
    private final PaymentProcessingService paymentProcessingService;

    public PaymentEnrollmentCreatedListener(
            IncomingEventMetadataExtractor metadataExtractor,
            IntegrationEventMessageValidator messageValidator,
            IdempotentEventProcessor eventProcessor,
            PaymentProcessingService paymentProcessingService
    ) {
        this.metadataExtractor = metadataExtractor;
        this.messageValidator = messageValidator;
        this.eventProcessor = eventProcessor;
        this.paymentProcessingService = paymentProcessingService;
    }

    @RabbitListener(queues = RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE)
    public void onMessage(Message message) {
        IncomingEventMetadata incoming = metadataExtractor.extract(message);
        EnrollmentCreatedEventV1 event = messageValidator.deserializeAndValidate(
                message,
                incoming,
                EnrollmentCreatedEventV1.class,
                EnrollmentCreatedEventV1.EVENT_TYPE,
                EnrollmentCreatedEventV1.VERSION
        );
        eventProcessor.process(
                incoming,
                EnrollmentCreatedEventV1.EVENT_TYPE,
                EnrollmentCreatedEventV1.VERSION,
                () -> paymentProcessingService.process(event)
        );
    }
}
