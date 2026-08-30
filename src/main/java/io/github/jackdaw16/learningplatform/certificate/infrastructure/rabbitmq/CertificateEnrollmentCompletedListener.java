package io.github.jackdaw16.learningplatform.certificate.infrastructure.rabbitmq;

import io.github.jackdaw16.learningplatform.certificate.application.CertificateIssuanceService;
import io.github.jackdaw16.learningplatform.messaging.EnrollmentCompletedEventV1;
import io.github.jackdaw16.learningplatform.messaging.RabbitTopology;
import io.github.jackdaw16.learningplatform.messaging.application.IdempotentEventProcessor;
import io.github.jackdaw16.learningplatform.messaging.application.IncomingEventMetadata;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IncomingEventMetadataExtractor;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IntegrationEventMessageValidator;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CertificateEnrollmentCompletedListener {

    private final IncomingEventMetadataExtractor metadataExtractor;
    private final IntegrationEventMessageValidator messageValidator;
    private final IdempotentEventProcessor eventProcessor;
    private final CertificateIssuanceService certificateIssuanceService;

    public CertificateEnrollmentCompletedListener(
            IncomingEventMetadataExtractor metadataExtractor,
            IntegrationEventMessageValidator messageValidator,
            IdempotentEventProcessor eventProcessor,
            CertificateIssuanceService certificateIssuanceService
    ) {
        this.metadataExtractor = metadataExtractor;
        this.messageValidator = messageValidator;
        this.eventProcessor = eventProcessor;
        this.certificateIssuanceService = certificateIssuanceService;
    }

    @RabbitListener(queues = RabbitTopology.CERTIFICATE_ENROLLMENT_COMPLETED_QUEUE)
    public void onMessage(Message message) {
        IncomingEventMetadata incoming = metadataExtractor.extract(message);
        EnrollmentCompletedEventV1 event = messageValidator.deserializeAndValidate(
                message,
                incoming,
                EnrollmentCompletedEventV1.class,
                EnrollmentCompletedEventV1.EVENT_TYPE,
                EnrollmentCompletedEventV1.VERSION
        );
        eventProcessor.process(
                incoming,
                EnrollmentCompletedEventV1.EVENT_TYPE,
                EnrollmentCompletedEventV1.VERSION,
                () -> certificateIssuanceService.issue(event)
        );
    }
}
