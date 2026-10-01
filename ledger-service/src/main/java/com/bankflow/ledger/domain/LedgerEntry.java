package com.bankflow.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row in the double-entry ledger.
 *
 * <p>Every transfer produces exactly TWO rows: a DEBIT on the sender's account
 * (negative amount) and a CREDIT on the receiver's account (positive amount).
 * The two rows must sum to zero — enforced in {@link com.bankflow.ledger.service.LedgerService}.
 *
 * <p><strong>Immutability contract:</strong> this entity is NEVER updated or deleted.
 * All JPA lifecycle callbacks and Hibernate DDL are configured to enforce append-only.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The transfer that caused this ledger movement. */
    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    /** The account being debited or credited. */
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /**
     * Signed amount: positive for CREDIT, negative for DEBIT.
     * Stored as NUMERIC(19,4) to avoid floating-point errors.
     */
    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 10)
    private EntryType entryType;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Factory method. Amount must already be signed correctly (negative for DEBIT). */
    public static LedgerEntry of(UUID transferId, UUID accountId, BigDecimal amount,
                                  EntryType entryType, String currency, String description) {
        LedgerEntry e = new LedgerEntry();
        e.transferId  = transferId;
        e.accountId   = accountId;
        e.amount      = amount;
        e.entryType   = entryType;
        e.currency    = currency;
        e.description = description;
        return e;
    }
}
