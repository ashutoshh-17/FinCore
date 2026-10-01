package com.bankflow.ledger.exception;

/**
 * Thrown on an attempt to write ledger entries for an already-recorded transferId
 * when idempotency logic determines the payload is inconsistent.
 */
public class LedgerAlreadyRecordedException extends RuntimeException {
    public LedgerAlreadyRecordedException(String message) {
        super(message);
    }
}
