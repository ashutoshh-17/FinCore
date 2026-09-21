package com.bankflow.transaction.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single money-movement request (transfer, deposit, or withdrawal).
 *
 * <p>Key invariants:
 * <ul>
 *   <li>I1 — The DB UNIQUE(initiated_by, idempotency_key) constraint enforces at-most-once creation.</li>
 *   <li>I9 — Money is {@link BigDecimal} with NUMERIC(19,4) in Postgres.</li>
 * </ul>
 */
@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    /**
     * SHA-256 hash of the serialized request body. Used to detect duplicate keys
     * with different payloads (→ 409 IDEMPOTENCY_KEY_REUSED per invariant I1).
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /** The authenticated user who initiated this transfer. */
    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    /** Amount must be positive (enforced by DB check constraint). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Setter
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Setter
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── Factory ───────────────────────────────────────────────────

    public static Transfer create(String idempotencyKey, String requestHash, UUID initiatedBy,
                                   UUID fromAccountId, UUID toAccountId,
                                   BigDecimal amount, String currency) {
        Transfer t = new Transfer();
        t.idempotencyKey  = idempotencyKey;
        t.requestHash     = requestHash;
        t.initiatedBy     = initiatedBy;
        t.fromAccountId   = fromAccountId;
        t.toAccountId     = toAccountId;
        t.amount          = amount;
        t.currency        = currency;
        t.status          = TransferStatus.PENDING;
        return t;
    }
}
