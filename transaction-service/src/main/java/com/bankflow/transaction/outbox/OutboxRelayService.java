package com.bankflow.transaction.outbox;

import com.bankflow.transaction.domain.OutboxEvent;
import com.bankflow.transaction.repository.OutboxEventRepository;
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
 * Scheduled relay that publishes unpublished outbox events to the transaction.events Kafka topic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private static final String TOPIC    = "transaction.events";
    private static final String PRODUCER = "transaction-service";

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
                        event.getEventType(), 1,
                        MaskingUtils.currentCorrelationId(), PRODUCER, event.getPayload()
                );
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), envelope);
                event.markPublished();
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event id={}: {}", event.getId(), ex.getMessage(), ex);
            }
        }

        log.debug("Outbox relay: published {} event(s)", pending.size());
    }
}
