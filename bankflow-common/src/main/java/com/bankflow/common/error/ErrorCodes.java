package com.bankflow.common.error;

/**
 * Centralised, stable machine-readable error codes used across all Bankflow services.
 *
 * <p>These codes appear in {@link ApiErrorResponse#code()} and must never be renamed once
 * published, as clients depend on them programmatically. Add new codes freely; deprecate
 * by adding a note comment; remove only in a major API version.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // ── Validation ────────────────────────────────────────────────
    public static final String VALIDATION_FAILED         = "VALIDATION_FAILED";
    public static final String MISSING_IDEMPOTENCY_KEY   = "MISSING_IDEMPOTENCY_KEY";
    public static final String INVALID_REQUEST           = "INVALID_REQUEST";

    // ── Idempotency ───────────────────────────────────────────────
    public static final String IDEMPOTENCY_KEY_REUSED    = "IDEMPOTENCY_KEY_REUSED";

    // ── Auth ──────────────────────────────────────────────────────
    public static final String UNAUTHORIZED              = "UNAUTHORIZED";
    public static final String FORBIDDEN                 = "FORBIDDEN";
    public static final String INVALID_CREDENTIALS       = "INVALID_CREDENTIALS";
    public static final String TOKEN_EXPIRED             = "TOKEN_EXPIRED";
    public static final String TOKEN_INVALID             = "TOKEN_INVALID";
    public static final String EMAIL_ALREADY_REGISTERED  = "EMAIL_ALREADY_REGISTERED";

    // ── Account ───────────────────────────────────────────────────
    public static final String ACCOUNT_NOT_FOUND         = "ACCOUNT_NOT_FOUND";
    public static final String ACCOUNT_NOT_ACTIVE        = "ACCOUNT_NOT_ACTIVE";
    public static final String ACCOUNT_FROZEN            = "ACCOUNT_FROZEN";
    public static final String ACCOUNT_CLOSED            = "ACCOUNT_CLOSED";

    // ── Transfer / Money ──────────────────────────────────────────
    public static final String TRANSFER_NOT_FOUND        = "TRANSFER_NOT_FOUND";
    public static final String INSUFFICIENT_FUNDS        = "INSUFFICIENT_FUNDS";
    public static final String SELF_TRANSFER             = "SELF_TRANSFER";
    public static final String DAILY_LIMIT_EXCEEDED      = "DAILY_LIMIT_EXCEEDED";
    public static final String AMOUNT_INVALID            = "AMOUNT_INVALID";

    // ── Saga / Saga compensation ───────────────────────────────────
    public static final String SAGA_COMPENSATION_FAILED  = "SAGA_COMPENSATION_FAILED";

    // ── Generic ───────────────────────────────────────────────────
    public static final String NOT_FOUND                 = "NOT_FOUND";
    public static final String CONFLICT                  = "CONFLICT";
    public static final String INTERNAL_ERROR            = "INTERNAL_ERROR";
    public static final String SERVICE_UNAVAILABLE       = "SERVICE_UNAVAILABLE";
}
