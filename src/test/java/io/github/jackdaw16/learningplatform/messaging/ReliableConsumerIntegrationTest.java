package io.github.jackdaw16.learningplatform.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jackdaw16.learningplatform.messaging.application.IdempotentEventProcessor;
import io.github.jackdaw16.learningplatform.messaging.infrastructure.rabbitmq.IncomingEventMetadataExtractor;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest(properties = "messaging.outbox.poll-interval=1h")
@Testcontainers
@Import(ReliableConsumerIntegrationTest.TestConsumerConfiguration.class)
class ReliableConsumerIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final long RECEIVE_TIMEOUT_MILLIS = 10_000;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-alpine")
            .withAdminUser("test")
            .withAdminPassword("test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TestEffect testEffect;

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.addresses", RABBITMQ::getAmqpUrl);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @BeforeEach
    void cleanInfrastructure() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS consumer_test_effects (event_id UUID PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL)");
        jdbcTemplate.execute("TRUNCATE TABLE consumer_test_effects, processed_events");
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE);
            channel.queuePurge(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ);
            return null;
        });
        testEffect.reset();
    }

    @Test
    void processesAValidEventOnceAndRecordsIt() {
        UUID eventId = UUID.randomUUID();

        publish(validMessage(eventId));

        await(() -> processedEventCount(eventId) == 1 && effectCount(eventId) == 1);
        assertEquals(EnrollmentCreatedEventV1.EVENT_TYPE, processedEventType(eventId));
        assertEquals(1, testEffect.attempts(eventId));
        assertNoDeadLetter();
    }

    @Test
    void acknowledgesARepublishedEventIdWithoutRepeatingTheEffect() {
        UUID eventId = UUID.randomUUID();
        publish(validMessage(eventId));
        await(() -> processedEventCount(eventId) == 1 && effectCount(eventId) == 1);

        publish(validMessage(eventId));

        await(() -> queueIsEmpty(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE));
        assertEquals(1, effectCount(eventId));
        assertEquals(1, processedEventCount(eventId));
        assertEquals(1, testEffect.attempts(eventId));
        assertNoDeadLetter();
    }

    @Test
    void rollsBackTheEffectAndProcessedRecordWhenProcessingFailsAfterWritingTheEffect() {
        UUID eventId = UUID.randomUUID();
        testEffect.failAfterWriting(eventId);

        publish(validMessage(eventId));

        Message deadLetter = receiveDeadLetter();
        assertEquals(eventId.toString(), deadLetter.getMessageProperties().getMessageId());
        assertEquals(4, testEffect.attempts(eventId));
        assertEquals(0, effectCount(eventId));
        assertEquals(0, processedEventCount(eventId));
    }

    @Test
    void retriesTransientFailuresThenProcessesTheEvent() {
        UUID eventId = UUID.randomUUID();
        testEffect.failNextAttempts(eventId, 2);

        publish(validMessage(eventId));

        await(() -> processedEventCount(eventId) == 1 && effectCount(eventId) == 1);
        assertEquals(3, testEffect.attempts(eventId));
        assertNoDeadLetter();
    }

    @Test
    void deadLettersPoisonMessagesAndContinuesWithTheNextMainQueueMessage() {
        UUID poisonId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();
        testEffect.failAlways(poisonId);

        publish(validMessage(poisonId));
        publish(validMessage(validId));

        await(() -> processedEventCount(validId) == 1 && effectCount(validId) == 1);
        Message deadLetter = receiveDeadLetter();
        assertEquals(poisonId.toString(), deadLetter.getMessageProperties().getMessageId());
        assertEquals(4, testEffect.attempts(poisonId));
        assertEquals(0, processedEventCount(poisonId));
        assertTrue(queueIsEmpty(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE));
    }

    @Test
    void deadLettersEventsWithMissingOrMalformedRequiredMetadata() {
        UUID missingTypeId = UUID.randomUUID();
        UUID unsupportedVersionId = UUID.randomUUID();
        UUID malformedVersionId = UUID.randomUUID();
        publish(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setHeader("eventType", EnrollmentCreatedEventV1.EVENT_TYPE)
                .setHeader("eventVersion", EnrollmentCreatedEventV1.VERSION)
                .build());
        publish(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setMessageId("not-a-uuid")
                .setHeader("eventType", EnrollmentCreatedEventV1.EVENT_TYPE)
                .setHeader("eventVersion", EnrollmentCreatedEventV1.VERSION)
                .build());
        publish(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setMessageId(missingTypeId.toString())
                .setHeader("eventVersion", EnrollmentCreatedEventV1.VERSION)
                .build());
        publish(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setMessageId(unsupportedVersionId.toString())
                .setHeader("eventType", EnrollmentCreatedEventV1.EVENT_TYPE)
                .setHeader("eventVersion", EnrollmentCreatedEventV1.VERSION + 1)
                .build());
        publish(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setMessageId(malformedVersionId.toString())
                .setHeader("eventType", EnrollmentCreatedEventV1.EVENT_TYPE)
                .setHeader("eventVersion", "not-a-version")
                .build());

        List<Message> deadLetters = receiveDeadLetters(5);
        Set<String> messageIds = deadLetters.stream()
                .map(message -> message.getMessageProperties().getMessageId())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(messageIds.contains(null));
        assertTrue(messageIds.contains("not-a-uuid"));
        assertTrue(messageIds.contains(missingTypeId.toString()));
        assertTrue(messageIds.contains(unsupportedVersionId.toString()));
        assertTrue(messageIds.contains(malformedVersionId.toString()));
        assertEquals(0, processedEventCount(missingTypeId));
        assertEquals(0, processedEventCount(unsupportedVersionId));
        assertEquals(0, processedEventCount(malformedVersionId));
        assertFalse(deadLetters.isEmpty());
    }

    private void publish(Message message) {
        rabbitTemplate.send(
                RabbitTopology.EVENTS_EXCHANGE,
                RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY,
                message
        );
    }

    private Message validMessage(UUID eventId) {
        return MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId.toString())
                .setHeader("eventType", EnrollmentCreatedEventV1.EVENT_TYPE)
                .setHeader("eventVersion", EnrollmentCreatedEventV1.VERSION)
                .build();
    }

    private Message receiveDeadLetter() {
        Message message = rabbitTemplate.receive(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ, RECEIVE_TIMEOUT_MILLIS);
        assertNotNull(message, "Expected a message in the dedicated payment DLQ");
        return message;
    }

    private List<Message> receiveDeadLetters(int count) {
        List<Message> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(receiveDeadLetter());
        }
        return messages;
    }

    private void assertNoDeadLetter() {
        assertNull(rabbitTemplate.receive(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_DLQ, 250));
    }

    private int processedEventCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class,
                eventId
        );
    }

    private String processedEventType(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT event_type FROM processed_events WHERE event_id = ?",
                String.class,
                eventId
        );
    }

    private int effectCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consumer_test_effects WHERE event_id = ?",
                Integer.class,
                eventId
        );
    }

    private boolean queueIsEmpty(String queue) {
        return rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queue).getMessageCount() == 0);
    }

    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                new CountDownLatch(1).await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for listener processing", exception);
            }
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for listener processing");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConsumerConfiguration {

        @Bean
        TestEffect testEffect(JdbcTemplate jdbcTemplate) {
            return new TestEffect(jdbcTemplate);
        }

        @Bean
        TestEnrollmentCreatedListener testEnrollmentCreatedListener(
                IdempotentEventProcessor processor,
                IncomingEventMetadataExtractor metadataExtractor,
                TestEffect effect
        ) {
            return new TestEnrollmentCreatedListener(processor, metadataExtractor, effect);
        }
    }

    static class TestEnrollmentCreatedListener {

        private final IdempotentEventProcessor processor;
        private final IncomingEventMetadataExtractor metadataExtractor;
        private final TestEffect effect;

        TestEnrollmentCreatedListener(
                IdempotentEventProcessor processor,
                IncomingEventMetadataExtractor metadataExtractor,
                TestEffect effect
        ) {
            this.processor = processor;
            this.metadataExtractor = metadataExtractor;
            this.effect = effect;
        }

        @RabbitListener(queues = RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE)
        public void consume(Message message) {
            var metadata = metadataExtractor.extract(message);
            processor.process(metadata, EnrollmentCreatedEventV1.EVENT_TYPE, EnrollmentCreatedEventV1.VERSION,
                    () -> effect.apply(metadata.eventId()));
        }
    }

    static class TestEffect {

        private final JdbcTemplate jdbcTemplate;
        private final java.util.concurrent.ConcurrentHashMap<UUID, AtomicInteger> attempts = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentHashMap<UUID, AtomicInteger> remainingFailures = new java.util.concurrent.ConcurrentHashMap<>();
        private final Set<UUID> alwaysFailing = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final Set<UUID> failAfterWriting = java.util.concurrent.ConcurrentHashMap.newKeySet();

        TestEffect(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        void apply(UUID eventId) {
            attempts.computeIfAbsent(eventId, ignored -> new AtomicInteger()).incrementAndGet();
            if (alwaysFailing.contains(eventId) || failsBeforeWriting(eventId)) {
                throw new IllegalStateException("Forced test processing failure");
            }
            jdbcTemplate.update(
                    "INSERT INTO consumer_test_effects (event_id, created_at) VALUES (?, ?)",
                    eventId,
                    Timestamp.from(Instant.now())
            );
            if (failAfterWriting.contains(eventId)) {
                throw new IllegalStateException("Forced failure after test effect");
            }
        }

        void failNextAttempts(UUID eventId, int count) {
            remainingFailures.put(eventId, new AtomicInteger(count));
        }

        void failAlways(UUID eventId) {
            alwaysFailing.add(eventId);
        }

        void failAfterWriting(UUID eventId) {
            failAfterWriting.add(eventId);
        }

        int attempts(UUID eventId) {
            AtomicInteger value = attempts.get(eventId);
            return value == null ? 0 : value.get();
        }

        void reset() {
            attempts.clear();
            remainingFailures.clear();
            alwaysFailing.clear();
            failAfterWriting.clear();
        }

        private boolean failsBeforeWriting(UUID eventId) {
            AtomicInteger failures = remainingFailures.get(eventId);
            return failures != null && failures.getAndUpdate(value -> Math.max(0, value - 1)) > 0;
        }
    }
}
