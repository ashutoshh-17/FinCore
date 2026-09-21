package com.bankflow.account.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Internal request body for POST /internal/accounts/transfers/reverse
 * Compensation: undo a previously applied transfer.
 */
public record ReverseTransferRequest(

        @NotNull(message = "transferId is required")
        UUID transferId,

        @NotNull(message = "fromAccountId is required")
        UUID fromAccountId,

        @NotNull(message = "toAccountId is required")
        UUID toAccountId
) {}
