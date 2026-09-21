package com.bankflow.account.dto;

import com.bankflow.account.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/accounts
 */
public record OpenAccountRequest(

        @NotNull(message = "Account type is required")
        AccountType type,

        @NotNull(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. USD")
        String currency
) {}
