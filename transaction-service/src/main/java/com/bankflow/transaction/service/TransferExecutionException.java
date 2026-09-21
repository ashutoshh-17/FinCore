package com.bankflow.transaction.service;

/** Thrown when the Account Service call fails during transfer execution. */
public class TransferExecutionException extends RuntimeException {
    public TransferExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
