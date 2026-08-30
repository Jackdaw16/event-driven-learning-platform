package io.github.jackdaw16.learningplatform.messaging.infrastructure.scheduling;

import io.github.jackdaw16.learningplatform.messaging.infrastructure.persistence.PostgresOutboxPublicationWorker;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublicationPoller {

    private final PostgresOutboxPublicationWorker worker;
    private final AtomicBoolean polling = new AtomicBoolean();

    public OutboxPublicationPoller(PostgresOutboxPublicationWorker worker) {
        this.worker = worker;
    }

    @Scheduled(
            fixedDelayString = "${messaging.outbox.poll-interval:1s}",
            initialDelayString = "${messaging.outbox.poll-interval:1s}"
    )
    public void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.publishPending();
        } finally {
            polling.set(false);
        }
    }
}
