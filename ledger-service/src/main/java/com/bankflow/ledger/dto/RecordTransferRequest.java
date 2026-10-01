package com.bankflow.ledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Incoming request from the Transaction service to record a completed transfer.
 *
 * <p>The transaction-service sends this after successfully debiting/crediting
 * account balances. The ledger then creates two immutable rows (I2, I3).
 */
public record RecordTransferRequest(

        @NotNull UUID transferId,
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3)
        String currency
) {}
