package com.bankflow.transaction.service;

/** Thrown when a transfer is not found or does not belong to the requesting user. */
public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(String message) {
        super(message);
    }
}
