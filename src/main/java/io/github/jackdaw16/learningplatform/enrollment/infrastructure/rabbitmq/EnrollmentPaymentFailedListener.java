package io.github.jackdaw16.learningplatform.enrollment.infrastructure.rabbitmq;

import io.github.jackdaw16.learningplatform.enrollment.application.EnrollmentPaymentFailedService;
import io.github.jackdaw16.learningplatform.messaging.PaymentFailedEventV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.IdempotentEventProcessor;
import io.github.jackdaw16.learningplatform.messaging.application.IncomingEventMetadata;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IncomingEventMetadataExtractor;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IntegrationEventMessageValidator;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPaymentFailedListener {

    private final IncomingEventMetadataExtractor metadataExtractor;
    private final IntegrationEventMessageValidator messageValidator;
    private final IdempotentEventProcessor eventProcessor;
    private final EnrollmentPaymentFailedService enrollmentPaymentFailedService;

    public EnrollmentPaymentFailedListener(
            IncomingEventMetadataExtractor metadataExtractor,
            IntegrationEventMessageValidator messageValidator,
            IdempotentEventProcessor eventProcessor,
            EnrollmentPaymentFailedService enrollmentPaymentFailedService
    ) {
        this.metadataExtractor = metadataExtractor;
        this.messageValidator = messageValidator;
        this.eventProcessor = eventProcessor;
        this.enrollmentPaymentFailedService = enrollmentPaymentFailedService;
    }

    @RabbitListener(queues = RabbitTopology.ENROLLMENT_PAYMENT_FAILED_QUEUE)
    public void onMessage(Message message) {
        IncomingEventMetadata incoming = metadataExtractor.extract(message);
        PaymentFailedEventV1 event = messageValidator.deserializeAndValidate(
                message,
                incoming,
                PaymentFailedEventV1.class,
                PaymentFailedEventV1.EVENT_TYPE,
                PaymentFailedEventV1.VERSION
        );
        eventProcessor.process(
                incoming,
                PaymentFailedEventV1.EVENT_TYPE,
                PaymentFailedEventV1.VERSION,
                () -> enrollmentPaymentFailedService.fail(event)
        );
    }
}
