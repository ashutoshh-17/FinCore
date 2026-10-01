package com.bankflow.ledger.dto;

import com.bankflow.ledger.domain.EntryType;
import com.bankflow.ledger.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing ledger entry representation returned on statement endpoints.
 */
public record LedgerEntryResponse(
        UUID id,
        UUID transferId,
        UUID accountId,
        BigDecimal amount,
        EntryType entryType,
        String currency,
        String description,
        Instant createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(
                e.getId(),
                e.getTransferId(),
                e.getAccountId(),
                e.getAmount(),
                e.getEntryType(),
                e.getCurrency(),
                e.getDescription(),
                e.getCreatedAt()
        );
    }
}
