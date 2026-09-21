package com.bankflow.account.dto;

import com.bankflow.account.domain.Account;
import com.bankflow.account.domain.AccountStatus;
import com.bankflow.account.domain.AccountType;
import com.bankflow.common.util.MaskingUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response for account reads. Balance is included for the account owner only.
 */
public record AccountResponse(
        UUID          id,
        String        accountNumber,
        AccountType   type,
        String        currency,
        BigDecimal    balance,
        AccountStatus status,
        Instant       createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getId(),
                MaskingUtils.maskSuffix(a.getAccountNumber()),
                a.getType(),
                a.getCurrency(),
                a.getBalance(),
                a.getStatus(),
                a.getCreatedAt()
        );
    }
}
