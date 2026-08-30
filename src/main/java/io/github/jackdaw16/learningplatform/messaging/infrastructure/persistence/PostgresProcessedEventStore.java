package io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence;

import io.github.jackdaw16.learningplatform.messaging.application.ProcessedEvent;
import io.github.jackdaw16.learningplatform.messaging.application.port.ProcessedEventStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PostgresProcessedEventStore implements ProcessedEventStore {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public PostgresProcessedEventStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public boolean isProcessed(UUID eventId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM processed_events WHERE event_id = ?)",
                Boolean.class,
                eventId
        ));
    }

    @Override
    public boolean recordIfUnprocessed(ProcessedEvent event) {
        return jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (event_id) DO NOTHING",
                event.eventId(),
                event.eventType(),
                Timestamp.from(clock.instant())
        ) == 1;
    }
}
