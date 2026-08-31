package io.github.jackdaw16.learningplatform.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence.PostgresOutboxPublicationWorker;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest(properties = "messaging.outbox.poll-interval=1h")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxPublicationIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MILLIS = 5_000;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-alpine")
            .withAdminUser("test")
            .withAdminPassword("test");

    @Autowired
    private PostgresOutboxPublicationWorker worker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
    void cleanOutboxAndQueue() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE);
            return null;
        });
    }

    @Test
    void publishesRawPendingPayloadWithMetadataAndMarksItPublished() {
        String payload = "{\"amount\": 19.99, \"enrollmentId\": \"e-1\"}";
        UUID eventId = insertPendingEvent(RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY, payload);

        assertEquals(1, worker.publishPending());

        Message message = rabbitTemplate.receive(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE, RECEIVE_TIMEOUT_MILLIS);
        assertNotNull(message);
        assertEquals(payload, new String(message.getBody(), StandardCharsets.UTF_8));
        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertEquals(eventId.toString(), message.getMessageProperties().getMessageId());
        assertEquals(EnrollmentCreatedEventV1.EVENT_TYPE, message.getMessageProperties().getHeader("eventType"));
        assertEquals(1, ((Number) message.getMessageProperties().getHeader("eventVersion")).intValue());
        assertPublished(eventId, 1);
    }

    @Test
    void returnedPublicationStaysPendingAndCountsTheAttempt() {
        UUID eventId = insertPendingEvent("not.bound", "{\"unroutable\":true}");

        assertEquals(0, worker.publishPending());

        assertPending(eventId, 1);
    }

    @Test
    void publishedEventDoesNotRepublishOnTheNextPoll() {
        UUID eventId = insertPendingEvent(RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY, "{\"once\":true}");

        assertEquals(1, worker.publishPending());
        assertNotNull(rabbitTemplate.receive(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE, RECEIVE_TIMEOUT_MILLIS));
        assertEquals(0, worker.publishPending());
        assertNull(rabbitTemplate.receive(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE, 500));
        assertPublished(eventId, 1);
    }

    @Test
    void concurrentWorkersPublishTheSamePendingRowOnlyOnce() throws Exception {
        UUID eventId = insertPendingEvent(RabbitTopology.ENROLLMENT_CREATED_ROUTING_KEY, "{\"concurrent\":true}");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> publishWhenStarted(ready, start));
            Future<Integer> second = executor.submit(() -> publishWhenStarted(ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
            assertNotNull(rabbitTemplate.receive(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE, RECEIVE_TIMEOUT_MILLIS));
            assertNull(rabbitTemplate.receive(RabbitTopology.PAYMENT_ENROLLMENT_CREATED_QUEUE, 500));
            assertPublished(eventId, 1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private int publishWhenStarted(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to publish");
            }
            return worker.publishPending();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting to publish", exception);
        }
    }

    private UUID insertPendingEvent(String routingKey, String payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO outbox_events (event_id, event_type, event_version, occurred_at, routing_key, payload, "
                        + "status, created_at, published_at, attempt_count) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', ?, NULL, 0)",
                eventId,
                EnrollmentCreatedEventV1.EVENT_TYPE,
                1,
                Timestamp.from(now),
                routingKey,
                payload,
                Timestamp.from(now)
        );
        return eventId;
    }

    private void assertPublished(UUID eventId, int attempts) {
        OutboxState state = stateOf(eventId);
        assertEquals("PUBLISHED", state.status());
        assertNotNull(state.publishedAt());
        assertEquals(attempts, state.attemptCount());
    }

    private void assertPending(UUID eventId, int attempts) {
        OutboxState state = stateOf(eventId);
        assertEquals("PENDING", state.status());
        assertNull(state.publishedAt());
        assertEquals(attempts, state.attemptCount());
    }

    private OutboxState stateOf(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status, published_at, attempt_count FROM outbox_events WHERE event_id = ?",
                (resultSet, rowNum) -> new OutboxState(
                        resultSet.getString("status"),
                        resultSet.getTimestamp("published_at") == null
                                ? null
                                : resultSet.getTimestamp("published_at").toInstant(),
                        resultSet.getInt("attempt_count")
                ),
                eventId
        );
    }

    private record OutboxState(String status, Instant publishedAt, int attemptCount) {
    }
}
