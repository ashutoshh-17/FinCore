package com.bankflow.ledger.exception;

/**
 * Thrown when the two entries for a transfer do not sum to zero.
 * This is a programming error, never a user error.
 */
public class LedgerBalanceViolationException extends RuntimeException {
    public LedgerBalanceViolationException(String message) {
        super(message);
    }
}
