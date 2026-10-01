package com.bankflow.ledger.repository;

import com.bankflow.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ledger entries — read-heavy, append-only writes.
 * No update or delete methods are defined here intentionally.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** All entries for a given account, newest first. */
    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /** All entries for a given transfer (should always be exactly 2 for a normal transfer). */
    List<LedgerEntry> findByTransferId(UUID transferId);

    /** Whether any entries exist for a given transferId (idempotency check). */
    boolean existsByTransferId(UUID transferId);

    /** Computed balance for an account from the ledger (sum of all signed amounts). */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.accountId = :accountId")
    BigDecimal computeBalance(@Param("accountId") UUID accountId);
}
