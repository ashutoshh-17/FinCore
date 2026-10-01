package com.bankflow.transaction.service;

import com.bankflow.transaction.domain.Transfer;
import com.bankflow.transaction.domain.TransferStatus;
import com.bankflow.transaction.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Saga Recovery Job — enforces Invariant I6.
 *
 * <p>A transfer stuck in {@code DEBITED} or {@code COMPENSATING} means the process
 * crashed or the downstream service was unavailable. This scheduled job sweeps
 * the database every minute and retries the appropriate saga step.
 *
 * <ul>
 *   <li>{@code DEBITED} for &gt; 60 seconds → ledger call was never made or confirmed;
 *       trigger compensation (reverse account balances, mark FAILED).</li>
 *   <li>{@code COMPENSATING} for &gt; 60 seconds → reversal was not confirmed;
 *       retry compensation.</li>
 * </ul>
 *
 * <p>Each retry is idempotent: the Account Service's {@code reverseTransfer} and the
 * Ledger Service's {@code recordTransfer} both check by {@code transferId} and
 * return success if already applied.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaRecoveryJob {

    /** Transfers stuck longer than this threshold are considered stale. */
    private static final long STALE_THRESHOLD_SECONDS = 60L;

    private final TransferRepository transferRepository;
    private final TransferService    transferService;

    /**
     * Runs every 60 seconds.
     * Finds all stale DEBITED and COMPENSATING transfers and retries compensation.
     */
    @Scheduled(fixedDelay = 60_000)
    public void recoverStuckTransfers() {
        Instant cutoff = Instant.now().minus(STALE_THRESHOLD_SECONDS, ChronoUnit.SECONDS);

        // ── Recover DEBITED transfers ──────────────────────────────────
        List<Transfer> staleDebited = transferRepository
                .findByStatusAndUpdatedAtBefore(TransferStatus.DEBITED, cutoff);

        if (!staleDebited.isEmpty()) {
            log.warn("SagaRecoveryJob: found {} stale DEBITED transfer(s). Triggering compensation.",
                    staleDebited.size());
        }

        for (Transfer t : staleDebited) {
            log.warn("Compensating stale DEBITED transfer: transferId={} age={}s",
                    t.getId(),
                    ChronoUnit.SECONDS.between(t.getUpdatedAt(), Instant.now()));
            try {
                transferService.triggerCompensation(t, t.getInitiatedBy(),
                        "Saga recovery: ledger confirmation never received");
            } catch (Exception ex) {
                log.error("Recovery compensation failed for transferId={}: {}", t.getId(), ex.getMessage());
            }
        }

        // ── Retry COMPENSATING transfers ───────────────────────────────
        List<Transfer> staleCompensating = transferRepository
                .findByStatusAndUpdatedAtBefore(TransferStatus.COMPENSATING, cutoff);

        if (!staleCompensating.isEmpty()) {
            log.warn("SagaRecoveryJob: found {} stale COMPENSATING transfer(s). Retrying.",
                    staleCompensating.size());
        }

        for (Transfer t : staleCompensating) {
            log.warn("Retrying stale COMPENSATING transfer: transferId={}", t.getId());
            try {
                transferService.triggerCompensation(t, t.getInitiatedBy(),
                        "Saga recovery: retrying compensation");
            } catch (Exception ex) {
                log.error("Retry compensation failed for transferId={}: {}", t.getId(), ex.getMessage());
            }
        }
    }
}
