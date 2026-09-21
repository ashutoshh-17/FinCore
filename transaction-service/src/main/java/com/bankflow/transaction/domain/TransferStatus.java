package com.bankflow.transaction.domain;

/**
 * State machine for a transfer.
 *
 * <pre>
 * PENDING → COMPLETED
 *         → FAILED
 *         → COMPENSATING → FAILED
 * </pre>
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED,
    COMPENSATING
}
