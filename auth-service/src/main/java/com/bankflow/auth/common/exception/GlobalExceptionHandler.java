package com.bankflow.auth.common.exception;

import com.bankflow.auth.modules.auth.AuthException;
import com.bankflow.common.error.ApiErrorResponse;
import com.bankflow.common.error.ErrorCodes;
import com.bankflow.common.util.MaskingUtils;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralized exception handler for the Auth service.
 *
 * <p>Every exception is mapped to an {@link ApiErrorResponse} (RFC 9457 ProblemDetail shape).
 * Internal details (stack traces, SQL errors) are never exposed. The correlation id is always
 * included so support can trace the request across services.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Domain / business exceptions ─────────────────────────────

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException ex) {
        int status = statusForCode(ex.getCode());
        log.warn("Auth domain error [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(status).body(
                ApiErrorResponse.of(status, ex.getCode(), titleForCode(ex.getCode()),
                        ex.getMessage(), MaskingUtils.currentCorrelationId())
        );
    }

    // ── Validation ────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(400, ErrorCodes.VALIDATION_FAILED, "Validation failed",
                        detail, MaskingUtils.currentCorrelationId())
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of(400, ErrorCodes.VALIDATION_FAILED, "Validation failed",
                        detail, MaskingUtils.currentCorrelationId())
        );
    }

    // ── Spring Security ───────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiErrorResponse.of(401, ErrorCodes.UNAUTHORIZED, "Unauthorized",
                        "Authentication required", MaskingUtils.currentCorrelationId())
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiErrorResponse.of(403, ErrorCodes.FORBIDDEN, "Forbidden",
                        "You do not have permission to perform this action",
                        MaskingUtils.currentCorrelationId())
        );
    }

    // ── Catch-all ─────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error [correlationId={}]: {}",
                MaskingUtils.currentCorrelationId(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(
                ApiErrorResponse.of(500, ErrorCodes.INTERNAL_ERROR, "Internal server error",
                        "An unexpected error occurred. Please try again or contact support.",
                        MaskingUtils.currentCorrelationId())
        );
    }

    // ── Helpers ───────────────────────────────────────────────────

    private int statusForCode(String code) {
        return switch (code) {
            case ErrorCodes.EMAIL_ALREADY_REGISTERED, ErrorCodes.IDEMPOTENCY_KEY_REUSED -> 409;
            case ErrorCodes.INVALID_CREDENTIALS, ErrorCodes.TOKEN_INVALID, ErrorCodes.TOKEN_EXPIRED,
                 ErrorCodes.UNAUTHORIZED -> 401;
            case ErrorCodes.FORBIDDEN -> 403;
            case ErrorCodes.NOT_FOUND -> 404;
            default -> 422;
        };
    }

    private String titleForCode(String code) {
        return switch (code) {
            case ErrorCodes.EMAIL_ALREADY_REGISTERED -> "Email already registered";
            case ErrorCodes.INVALID_CREDENTIALS      -> "Invalid credentials";
            case ErrorCodes.TOKEN_INVALID            -> "Invalid token";
            case ErrorCodes.TOKEN_EXPIRED            -> "Token expired";
            case ErrorCodes.FORBIDDEN                -> "Forbidden";
            case ErrorCodes.NOT_FOUND                -> "Not found";
            default -> "Business rule violation";
        };
    }
}
