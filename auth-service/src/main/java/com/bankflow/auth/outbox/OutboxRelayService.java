package com.bankflow.auth.outbox;

import com.bankflow.common.event.EventEnvelope;
import com.bankflow.common.util.MaskingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled relay that publishes unpublished outbox events to Kafka.
 *
 * <p>Runs every 5 seconds. Fetches up to 100 unpublished rows, sends each to Kafka,
 * then marks it published in the same transaction. This guarantees at-least-once
 * delivery to Kafka even if the process crashes between DB commit and Kafka send.
 *
 * <p>Consumers must be idempotent (they use {@code processed_events}) to handle the
 * rare case where a row is published but {@code published_at} is not committed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private static final String TOPIC = "auth.events";
    private static final String PRODUCER = "auth-service";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublished();
        if (pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                EventEnvelope<Object> envelope = EventEnvelope.of(
                        event.getEventType(),
                        1,
                        MaskingUtils.currentCorrelationId(),
                        PRODUCER,
                        event.getPayload()
                );
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), envelope);
                event.markPublished();
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), ex.getMessage(), ex);
                // Leave published_at null — will be retried on next scheduled run
            }
        }

        log.debug("Outbox relay: published {} event(s)", pending.size());
    }
}
