package com.bankflow.transaction.domain;

/**
 * State machine for a transfer saga.
 *
 * <pre>
 * PENDING → DEBITED → COMPLETED
 *         → FAILED
 * DEBITED → COMPENSATING → FAILED  (ledger failed; account reversal triggered)
 * </pre>
 */
public enum TransferStatus {
    /** Initial state: saved to DB, account balances not yet changed. */
    PENDING,
    /** Account balances applied; waiting for ledger to confirm. */
    DEBITED,
    /** Ledger confirmed. Transfer fully complete. */
    COMPLETED,
    /** Compensation in progress: reversing account balances. */
    COMPENSATING,
    /** Terminal failure: all saga steps rolled back. */
    FAILED
}

