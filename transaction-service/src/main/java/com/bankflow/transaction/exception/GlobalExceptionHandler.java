package com.bankflow.transaction.exception;

import com.bankflow.common.error.ApiErrorResponse;
import com.bankflow.common.error.ErrorCodes;
import com.bankflow.common.util.MaskingUtils;
import com.bankflow.transaction.service.TransferExecutionException;
import com.bankflow.transaction.service.TransferNotFoundException;
import com.bankflow.transaction.service.TransferValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for transaction-service.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TransferNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), ErrorCodes.TRANSFER_NOT_FOUND,
                        "Transfer Not Found", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(TransferValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(TransferValidationException ex) {
        HttpStatus status = ErrorCodes.IDEMPOTENCY_KEY_REUSED.equals(ex.getErrorCode())
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(
                ApiErrorResponse.of(status.value(), ex.getErrorCode(),
                        "Transfer Validation Failed", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(TransferExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleExecutionFailure(TransferExecutionException ex) {
        log.error("Transfer execution failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ErrorCodes.INSUFFICIENT_FUNDS,
                        "Transfer Failed", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ErrorCodes.MISSING_IDEMPOTENCY_KEY,
                        "Missing Header", "Required header '" + ex.getHeaderName() + "' is missing",
                        MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationError(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst().orElse("Validation error");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ErrorCodes.VALIDATION_ERROR,
                        "Validation Error", msg, MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error [correlationId={}]: {}", MaskingUtils.currentCorrelationId(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCodes.INTERNAL_ERROR,
                        "Internal Server Error", "An unexpected error occurred",
                        MaskingUtils.currentCorrelationId()));
    }
}
