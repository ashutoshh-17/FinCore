package com.bankflow.account.service;

/**
 * Thrown when an account is not found or the requesting user does not own it.
 * Maps to HTTP 404 via the global exception handler.
 */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
