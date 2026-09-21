package com.bankflow.account.domain;

/**
 * Thrown when a debit is attempted on an account with insufficient funds.
 * Maps to HTTP 422 Unprocessable Entity via the global exception handler.
 */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
