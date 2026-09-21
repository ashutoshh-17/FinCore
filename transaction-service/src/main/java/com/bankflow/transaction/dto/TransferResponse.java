package com.bankflow.transaction.dto;

import com.bankflow.transaction.domain.Transfer;
import com.bankflow.transaction.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for transfer reads and creation.
 */
public record TransferResponse(
        UUID           id,
        String         idempotencyKey,
        UUID           fromAccountId,
        UUID           toAccountId,
        BigDecimal     amount,
        String         currency,
        TransferStatus status,
        String         failureReason,
        Instant        createdAt,
        Instant        updatedAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getIdempotencyKey(),
                t.getFromAccountId(),
                t.getToAccountId(),
                t.getAmount(),
                t.getCurrency(),
                t.getStatus(),
                t.getFailureReason(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
