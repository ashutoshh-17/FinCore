package com.bankflow.common.util;

import org.slf4j.MDC;

/**
 * Utility methods for masking sensitive values before they appear in logs or API responses.
 *
 * <p>Coding Standard rule: never log passwords, JWTs, refresh tokens, full account numbers
 * or PII. Use these helpers to produce safe representations.
 */
public final class MaskingUtils {

    /** Number of trailing characters to leave visible when masking. */
    private static final int VISIBLE_SUFFIX_LENGTH = 4;
    private static final String MASK = "****";

    private MaskingUtils() {}

    /**
     * Masks all but the last {@value #VISIBLE_SUFFIX_LENGTH} characters of a string.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code maskSuffix("ACC-123456789")} → {@code "****6789"}
     *   <li>{@code maskSuffix("short")}         → {@code "****"} (too short to show suffix)
     * </ul>
     *
     * @param value the string to mask; if {@code null} or blank, returns {@code "****"}
     */
    public static String maskSuffix(String value) {
        if (value == null || value.length() <= VISIBLE_SUFFIX_LENGTH) {
            return MASK;
        }
        return MASK + value.substring(value.length() - VISIBLE_SUFFIX_LENGTH);
    }

    /**
     * Returns the current correlation id from MDC, or {@code "N/A"} if not set.
     * Convenience for error handlers that build error responses before the filter runs.
     */
    public static String currentCorrelationId() {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        return (id != null) ? id : "N/A";
    }

    /**
     * Masks an email address, showing only the first character and the domain.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code maskEmail("alice@example.com")} → {@code "a****@example.com"}
     *   <li>{@code maskEmail("ab@x.io")}           → {@code "a****@x.io"}
     * </ul>
     *
     * @param email the email to mask; returns {@code "****"} if null/blank
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return MASK;
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) {
            return MASK;
        }
        return email.charAt(0) + MASK + email.substring(atIdx);
    }
}
