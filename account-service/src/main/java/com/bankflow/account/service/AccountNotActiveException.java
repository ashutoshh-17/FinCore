package com.bankflow.account.service;

/**
 * Thrown when an operation requires an ACTIVE account but the account is FROZEN or CLOSED.
 */
public class AccountNotActiveException extends RuntimeException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
