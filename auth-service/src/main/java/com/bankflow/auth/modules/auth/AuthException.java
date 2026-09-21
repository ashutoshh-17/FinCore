package com.bankflow.auth.modules.auth;

import lombok.Getter;

/**
 * Domain exception thrown for authentication and authorization failures in the Auth service.
 * Mapped to HTTP responses by {@link com.bankflow.auth.common.exception.GlobalExceptionHandler}.
 */
@Getter
public class AuthException extends RuntimeException {

    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }
}
