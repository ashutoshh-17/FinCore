package com.bankflow.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard event envelope wrapping every Kafka message produced by Bankflow services.
 *
 * <p>All fields except {@code correlationId} are required. Consumers must treat unknown
 * fields in {@code payload} as forward-compatible additions.
 *
 * <p>Invariant: {@code eventId} is globally unique and stable; producing the same logical
 * event twice produces two records with distinct {@code eventId}s (idempotency is enforced
 * via the outbox pattern, not by deduplicating the envelope).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(

        /** Globally unique identifier for this event occurrence. */
        UUID eventId,

        /**
         * Stable, machine-readable name of the event (e.g. {@code TransferCompleted}).
         * Must not be renamed — use a new {@code eventVersion} for breaking changes.
         */
        String eventType,

        /**
         * Schema version. Increment when the {@code payload} shape changes incompatibly.
         * Start at 1.
         */
        int eventVersion,

        /** UTC timestamp when the business event occurred, not when the message was sent. */
        Instant occurredAt,

        /**
         * Correlation id from the originating HTTP request. Flows through HTTP calls and
         * Kafka envelopes so a single transfer can be traced across all services.
         */
        String correlationId,

        /** Name of the service that produced this event (e.g. {@code transaction-service}). */
        String producer,

        /** Event-specific data. Must contain IDs, amounts and statuses only — no PII or secrets. */
        T payload
) {

    /**
     * Convenience factory: creates an envelope with a fresh {@code eventId} and {@code occurredAt = now()}.
     */
    public static <T> EventEnvelope<T> of(
            String eventType,
            int eventVersion,
            String correlationId,
            String producer,
            T payload
    ) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                eventVersion,
                Instant.now(),
                correlationId,
                producer,
                payload
        );
    }
}
