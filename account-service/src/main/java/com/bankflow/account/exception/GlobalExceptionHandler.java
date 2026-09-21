package com.bankflow.account.exception;

import com.bankflow.account.domain.InsufficientFundsException;
import com.bankflow.account.service.AccountNotActiveException;
import com.bankflow.account.service.AccountNotFoundException;
import com.bankflow.common.error.ApiErrorResponse;
import com.bankflow.common.error.ErrorCodes;
import com.bankflow.common.util.MaskingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for account-service.
 * Maps domain exceptions to standardized {@link ApiErrorResponse} payloads.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), ErrorCodes.ACCOUNT_NOT_FOUND,
                        "Account Not Found", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ErrorCodes.INSUFFICIENT_FUNDS,
                        "Insufficient Funds", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotActive(AccountNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ErrorCodes.ACCOUNT_NOT_ACTIVE,
                        "Account Not Active", ex.getMessage(), MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict (retries exhausted): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiErrorResponse.of(HttpStatus.CONFLICT.value(), ErrorCodes.CONCURRENT_MODIFICATION,
                        "Concurrent Modification",
                        "Request conflicted with a concurrent update. Please retry.",
                        MaskingUtils.currentCorrelationId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
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
