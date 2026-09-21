package com.bankflow.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal request body for POST /internal/accounts/transfers/apply
 * Sent by the Transaction service only. Protected by the internal secret header.
 */
public record ApplyTransferRequest(

        @NotNull(message = "transferId is required")
        UUID transferId,

        @NotNull(message = "fromAccountId is required")
        UUID fromAccountId,

        @NotNull(message = "toAccountId is required")
        UUID toAccountId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0001", message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "currency is required")
        String currency
) {}
