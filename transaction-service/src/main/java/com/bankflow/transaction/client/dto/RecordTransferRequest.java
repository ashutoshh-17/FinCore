package com.bankflow.transaction.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO sent to the Ledger Service's /internal/ledger/record endpoint.
 */
public record RecordTransferRequest(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency
) {}
