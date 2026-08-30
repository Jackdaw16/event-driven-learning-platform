CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT outbox_events_status_valid CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX outbox_events_pending_created_at_idx
    ON outbox_events (status, created_at)
    WHERE status = 'PENDING';
