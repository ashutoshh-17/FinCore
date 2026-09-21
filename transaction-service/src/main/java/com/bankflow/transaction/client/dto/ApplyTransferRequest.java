package com.bankflow.transaction.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Mirrors account-service's ApplyTransferRequest DTO — sent over Feign. */
public record ApplyTransferRequest(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency
) {}
