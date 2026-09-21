package com.bankflow.account.domain;

/**
 * Possible account types.
 * SYSTEM is used for the special SYSTEM_CASH account (counterparty for deposits/withdrawals).
 */
public enum AccountType {
    SAVINGS,
    CHECKING,
    SYSTEM
}
