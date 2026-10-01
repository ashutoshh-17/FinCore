package com.bankflow.ledger.service;

import com.bankflow.ledger.domain.EntryType;
import com.bankflow.ledger.domain.LedgerEntry;
import com.bankflow.ledger.dto.LedgerEntryResponse;
import com.bankflow.ledger.dto.RecordTransferRequest;
import com.bankflow.ledger.exception.LedgerAlreadyRecordedException;
import com.bankflow.ledger.exception.LedgerBalanceViolationException;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Core business logic for the ledger.
 *
 * <p>Key invariants enforced here:
 * <ul>
 *   <li>I2 — For every transfer, ledger entries sum to zero.</li>
 *   <li>I3 — {@code ledger_entries} rows are never updated or deleted.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Records a completed transfer as two immutable ledger entries.
     *
     * <p>Idempotent: if entries for this transferId already exist, returns them
     * without writing duplicates (safe for saga retries).
     *
     * @param req the transfer details supplied by the Transaction service
     * @return the two created (or previously existing) ledger entries
     * @throws LedgerBalanceViolationException if the computed entries would not sum to zero
     */
    @Transactional
    public List<LedgerEntry> recordTransfer(RecordTransferRequest req) {
        // ── Idempotency: if already recorded, return cached entries ──────
        if (ledgerEntryRepository.existsByTransferId(req.transferId())) {
            log.info("Ledger idempotent replay: transferId={}", req.transferId());
            return ledgerEntryRepository.findByTransferId(req.transferId());
        }

        // ── Build double-entry pair ──────────────────────────────────────
        // DEBIT the sender: money leaves, so amount is negative
        LedgerEntry debit = LedgerEntry.of(
                req.transferId(),
                req.fromAccountId(),
                req.amount().negate(),           // negative — money OUT
                EntryType.DEBIT,
                req.currency(),
                "Transfer debit to " + req.toAccountId()
        );

        // CREDIT the receiver: money arrives, so amount is positive
        LedgerEntry credit = LedgerEntry.of(
                req.transferId(),
                req.toAccountId(),
                req.amount(),                    // positive — money IN
                EntryType.CREDIT,
                req.currency(),
                "Transfer credit from " + req.fromAccountId()
        );

        // ── Validate: debit + credit must sum to zero (Invariant I2) ─────
        BigDecimal sum = debit.getAmount().add(credit.getAmount());
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new LedgerBalanceViolationException(
                    "Ledger balance violation for transferId=" + req.transferId() +
                    ": entries sum to " + sum + " instead of 0");
        }

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        log.info("Ledger recorded: transferId={} debit={} credit={}",
                req.transferId(), req.amount().negate(), req.amount());

        return List.of(debit, credit);
    }

    /**
     * Returns the paginated account statement (all ledger entries for an account).
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getStatement(UUID accountId, Pageable pageable) {
        return ledgerEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(LedgerEntryResponse::from);
    }

    /**
     * Returns the computed ledger balance for an account.
     * This is used by the reconciliation service to detect drift vs account-service balances.
     */
    @Transactional(readOnly = true)
    public BigDecimal computeLedgerBalance(UUID accountId) {
        return ledgerEntryRepository.computeBalance(accountId);
    }
}
