package com.bankflow.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal request body for POST /internal/accounts/transfers/reverse
 * Compensation: undo a previously applied transfer by crediting the sender and debiting the receiver.
 */
public record ReverseTransferRequest(

        @NotNull(message = "transferId is required")
        UUID transferId,

        @NotNull(message = "fromAccountId is required")
        UUID fromAccountId,

        @NotNull(message = "toAccountId is required")
        UUID toAccountId,

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3)
        String currency
) {}
