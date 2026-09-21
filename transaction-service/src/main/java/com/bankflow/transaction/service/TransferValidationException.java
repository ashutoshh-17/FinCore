package com.bankflow.transaction.service;

/** Thrown when transfer business rule validation fails (self-transfer, idempotency reuse). */
public class TransferValidationException extends RuntimeException {
    private final String errorCode;

    public TransferValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
