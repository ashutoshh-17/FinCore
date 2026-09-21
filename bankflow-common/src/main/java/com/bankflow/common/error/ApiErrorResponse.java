package com.bankflow.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * RFC 9457 Problem Detail error response shape, used by every service's
 * {@code GlobalExceptionHandler}.
 *
 * <p>The {@code code} field carries a stable, machine-readable string so that API
 * consumers can react programmatically (e.g. {@code INSUFFICIENT_FUNDS},
 * {@code IDEMPOTENCY_KEY_REUSED}).
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "type":          "https://bankflow.dev/errors/insufficient-funds",
 *   "title":         "Insufficient funds",
 *   "status":        422,
 *   "code":          "INSUFFICIENT_FUNDS",
 *   "detail":        "Source account balance is too low for this transfer.",
 *   "correlationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
 *   "timestamp":     "2025-01-01T10:00:00Z"
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(

        /** Problem type URI (stable across versions). */
        String type,

        /** Short, human-readable summary of the error type. */
        String title,

        /** HTTP status code. */
        int status,

        /** Stable machine-readable error code for programmatic consumers. */
        String code,

        /** Human-readable explanation specific to this occurrence. Never contains secrets or stack traces. */
        String detail,

        /** Correlation id from the originating request, for cross-service tracing. */
        String correlationId,

        /** UTC timestamp of this error response. */
        Instant timestamp
) {
    private static final String BASE_URI = "https://bankflow.dev/errors/";

    /**
     * Builds a response with {@code timestamp = now()} and a derived {@code type} URI.
     *
     * @param status        HTTP status code
     * @param code          stable machine-readable code in UPPER_SNAKE_CASE
     * @param title         short human-readable title
     * @param detail        human-readable explanation (no internal details)
     * @param correlationId request correlation id
     */
    public static ApiErrorResponse of(
            int status,
            String code,
            String title,
            String detail,
            String correlationId
    ) {
        String slug = code.toLowerCase().replace('_', '-');
        return new ApiErrorResponse(
                BASE_URI + slug,
                title,
                status,
                code,
                detail,
                correlationId,
                Instant.now()
        );
    }
}
