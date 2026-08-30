package io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.messaging.application.PendingOutboxEvent;
import io.github.jackdaw16.learningplatform.messaging.application.port.IntegrationEventPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PostgresOutboxPublicationWorker {

    private static final String PENDING_STATUS = "PENDING";
    private static final String PUBLISHED_STATUS = "PUBLISHED";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final IntegrationEventPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public PostgresOutboxPublicationWorker(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            IntegrationEventPublisher publisher,
            Clock clock,
            @Value("${messaging.outbox.batch-size:20}") int batchSize
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public int publishPending() {
        int published = 0;
        for (UUID eventId : pendingEventIds()) {
            Boolean wasPublished = transactionTemplate.execute(status -> publishLocked(eventId));
            if (Boolean.TRUE.equals(wasPublished)) {
                published++;
            }
        }
        return published;
    }

    private List<UUID> pendingEventIds() {
        return jdbcTemplate.query(
                "SELECT event_id FROM outbox_events WHERE status = ? ORDER BY created_at, event_id LIMIT ?",
                (resultSet, rowNum) -> resultSet.getObject("event_id", UUID.class),
                PENDING_STATUS,
                batchSize
        );
    }

    private boolean publishLocked(UUID eventId) {
        PendingOutboxEvent event = jdbcTemplate.query(
                "SELECT event_id, event_type, event_version, occurred_at, routing_key, payload::text AS payload, attempt_count "
                        + "FROM outbox_events WHERE event_id = ? AND status = ? FOR UPDATE SKIP LOCKED",
                resultSet -> resultSet.next() ? new PendingOutboxEvent(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getInt("event_version"),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getString("routing_key"),
                        resultSet.getString("payload"),
                        resultSet.getInt("attempt_count")
                ) : null,
                eventId,
                PENDING_STATUS
        );
        if (event == null) {
            return false;
        }

        try {
            publisher.publish(event);
            jdbcTemplate.update(
                    "UPDATE outbox_events SET status = ?, published_at = ?, attempt_count = attempt_count + 1 "
                            + "WHERE event_id = ? AND status = ?",
                    PUBLISHED_STATUS,
                    Timestamp.from(clock.instant()),
                    eventId,
                    PENDING_STATUS
            );
            return true;
        } catch (RuntimeException exception) {
            jdbcTemplate.update(
                    "UPDATE outbox_events SET status = ?, published_at = NULL, attempt_count = attempt_count + 1 "
                            + "WHERE event_id = ? AND status = ?",
                    PENDING_STATUS,
                    eventId,
                    PENDING_STATUS
            );
            return false;
        }
    }
}
