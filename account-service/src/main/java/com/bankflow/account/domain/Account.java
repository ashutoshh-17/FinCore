package com.bankflow.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The Account aggregate root.
 *
 * <p>Invariants enforced here:
 * <ul>
 *   <li>I4 — balance never goes below zero (DB check + service guard).</li>
 *   <li>Optimistic locking via {@code @Version} prevents lost updates under concurrency.</li>
 *   <li>Money is always {@link BigDecimal} (never double/float).</li>
 * </ul>
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    /**
     * The user who owns this account. References auth_db.users — no FK across services (I8).
     */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Cached balance. The Ledger service is the source of truth for history;
     * this is the up-to-date balance for fast reads and overdraft checks.
     * NUMERIC(19,4) in Postgres — matches BigDecimal scale 4.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Setter
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    /**
     * Hibernate optimistic locking column. Incremented automatically on each UPDATE.
     * Any concurrent modifier that loaded a stale version will fail with
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.
     */
    @Version
    private Long version;

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

    public static Account open(UUID ownerId, AccountType type, String currency, String accountNumber) {
        Account a = new Account();
        a.ownerId       = ownerId;
        a.type          = type;
        a.currency      = currency;
        a.accountNumber = accountNumber;
        a.balance       = BigDecimal.ZERO;
        a.status        = AccountStatus.ACTIVE;
        return a;
    }

    // ── Money mutations ───────────────────────────────────────────

    /**
     * Debit the account by the given amount. Throws if the balance would go negative
     * (enforces I4 in the application layer before hitting the DB check).
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive, got: " + amount);
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance=" + balance + " debit=" + amount);
        }
        this.balance = balance.subtract(amount);
    }

    /**
     * Credit the account by the given amount.
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive, got: " + amount);
        }
        this.balance = balance.add(amount);
    }
}
