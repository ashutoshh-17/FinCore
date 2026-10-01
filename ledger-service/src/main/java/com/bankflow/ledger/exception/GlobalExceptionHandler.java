package com.bankflow.ledger.exception;

import com.bankflow.common.error.ApiErrorResponse;
import com.bankflow.common.util.MaskingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps ledger-service domain exceptions to standardised {@link ApiErrorResponse} payloads.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(LedgerBalanceViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleBalanceViolation(LedgerBalanceViolationException ex) {
        log.error("Ledger balance violation [correlationId={}]: {}", MaskingUtils.currentCorrelationId(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(500, "LEDGER_BALANCE_VIOLATION",
                        "Ledger Balance Violation", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(LedgerAlreadyRecordedException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyRecorded(LedgerAlreadyRecordedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiErrorResponse.of(409, "LEDGER_ALREADY_RECORDED",
                        "Ledger Already Recorded", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst().orElse("Validation error");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiErrorResponse.of(400, "VALIDATION_ERROR", "Validation Error", msg,
                        MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error [correlationId={}]: {}", MaskingUtils.currentCorrelationId(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(500, "INTERNAL_ERROR", "Internal Server Error",
                        "An unexpected error occurred", MaskingUtils.currentCorrelationId()));
    }
}
