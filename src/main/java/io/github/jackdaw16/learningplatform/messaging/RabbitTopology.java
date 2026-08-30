package io.github.jackdaw16.learningplatform.messaging;

public final class RabbitTopology {

    public static final String EVENTS_EXCHANGE = "learning.events";
    public static final String DEAD_LETTER_EXCHANGE = "learning.dlx";

    public static final String ENROLLMENT_CREATED_ROUTING_KEY = EnrollmentCreatedEventV1.EVENT_TYPE;
    public static final String PAYMENT_CONFIRMED_ROUTING_KEY = PaymentConfirmedEventV1.EVENT_TYPE;
    public static final String PAYMENT_FAILED_ROUTING_KEY = PaymentFailedEventV1.EVENT_TYPE;
    public static final String ENROLLMENT_COMPLETED_ROUTING_KEY = EnrollmentCompletedEventV1.EVENT_TYPE;

    public static final String PAYMENT_ENROLLMENT_CREATED_QUEUE = "payment.enrollment-created.q";
    public static final String ENROLLMENT_PAYMENT_CONFIRMED_QUEUE = "enrollment.payment-confirmed.q";
    public static final String ENROLLMENT_PAYMENT_FAILED_QUEUE = "enrollment.payment-failed.q";
    public static final String CERTIFICATE_ENROLLMENT_COMPLETED_QUEUE = "certificate.enrollment-completed.q";

    public static final String PAYMENT_ENROLLMENT_CREATED_DLQ = "payment.enrollment-created.dlq";
    public static final String ENROLLMENT_PAYMENT_CONFIRMED_DLQ = "enrollment.payment-confirmed.dlq";
    public static final String ENROLLMENT_PAYMENT_FAILED_DLQ = "enrollment.payment-failed.dlq";
    public static final String CERTIFICATE_ENROLLMENT_COMPLETED_DLQ = "certificate.enrollment-completed.dlq";

    public static final String PAYMENT_ENROLLMENT_CREATED_DLX_ROUTING_KEY = "payment.enrollment-created.dlq";
    public static final String ENROLLMENT_PAYMENT_CONFIRMED_DLX_ROUTING_KEY = "enrollment.payment-confirmed.dlq";
    public static final String ENROLLMENT_PAYMENT_FAILED_DLX_ROUTING_KEY = "enrollment.payment-failed.dlq";
    public static final String CERTIFICATE_ENROLLMENT_COMPLETED_DLX_ROUTING_KEY = "certificate.enrollment-completed.dlq";

    private RabbitTopology() {
    }
}
