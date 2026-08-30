package io.github.jackdaw16.learningplatform.enrollment.infrastructure.rabbitmq;

import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentPaymentConfirmedService;
import io.github.jackdaw16.learningplatform.messaging.PaymentConfirmedEventV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.IdempotentEventProcessor;
import io.github.jackdaw16.learningplatform.messaging.application.IncomingEventMetadata;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IncomingEventMetadataExtractor;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IntegrationEventMessageValidator;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPaymentConfirmedListener {

    private final IncomingEventMetadataExtractor metadataExtractor;
    private final IntegrationEventMessageValidator messageValidator;
    private final IdempotentEventProcessor eventProcessor;
    private final EnrollmentPaymentConfirmedService enrollmentPaymentConfirmedService;

    public EnrollmentPaymentConfirmedListener(
            IncomingEventMetadataExtractor metadataExtractor,
            IntegrationEventMessageValidator messageValidator,
            IdempotentEventProcessor eventProcessor,
            EnrollmentPaymentConfirmedService enrollmentPaymentConfirmedService
    ) {
        this.metadataExtractor = metadataExtractor;
        this.messageValidator = messageValidator;
        this.eventProcessor = eventProcessor;
        this.enrollmentPaymentConfirmedService = enrollmentPaymentConfirmedService;
    }

    @RabbitListener(queues = RabbitTopology.ENROLLMENT_PAYMENT_CONFIRMED_QUEUE)
    public void onMessage(Message message) {
        IncomingEventMetadata incoming = metadataExtractor.extract(message);
        PaymentConfirmedEventV1 event = messageValidator.deserializeAndValidate(
                message,
                incoming,
                PaymentConfirmedEventV1.class,
                PaymentConfirmedEventV1.EVENT_TYPE,
                PaymentConfirmedEventV1.VERSION
        );
        eventProcessor.process(
                incoming,
                PaymentConfirmedEventV1.EVENT_TYPE,
                PaymentConfirmedEventV1.VERSION,
                () -> enrollmentPaymentConfirmedService.confirm(event)
        );
    }
}
