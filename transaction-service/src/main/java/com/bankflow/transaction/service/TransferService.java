package com.bankflow.transaction.service;

import com.bankflow.transaction.client.AccountClient;
import com.bankflow.transaction.client.LedgerClient;
import com.bankflow.transaction.client.dto.ApplyTransferRequest;
import com.bankflow.transaction.client.dto.RecordTransferRequest;
import com.bankflow.transaction.client.dto.ReverseTransferRequest;
import com.bankflow.transaction.domain.OutboxEvent;
import com.bankflow.transaction.domain.Transfer;
import com.bankflow.transaction.domain.TransferStatus;
import com.bankflow.transaction.dto.CreateTransferRequest;
import com.bankflow.transaction.dto.TransferResponse;
import com.bankflow.transaction.repository.OutboxEventRepository;
import com.bankflow.transaction.repository.TransferRepository;
import com.bankflow.common.error.ErrorCodes;
import com.bankflow.common.util.MaskingUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Orchestrates the distributed transfer saga.
 *
 * <p><strong>Phase 3 saga flow:</strong>
 * <ol>
 *   <li>Save transfer as {@code PENDING}.</li>
 *   <li>Call Account Service → apply balances (debit sender, credit receiver).</li>
 *   <li>Update transfer to {@code DEBITED} (committed immediately).</li>
 *   <li>Call Ledger Service → record double-entry entries.</li>
 *   <li>If Ledger succeeds → update to {@code COMPLETED}.</li>
 *   <li>If Ledger fails → set to {@code COMPENSATING}, call Account Service to reverse,
 *       then set to {@code FAILED}.</li>
 * </ol>
 *
 * <p>If the process crashes between steps 3 and 4, the
 * {@link SagaRecoveryJob} will detect the {@code DEBITED} state and retry/compensate.
 *
 * <p>Invariant I6 is maintained: a transfer is never left half-applied.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository    transferRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AccountClient         accountClient;
    private final LedgerClient          ledgerClient;
    private final ObjectMapper          objectMapper;

    @Value("${internal.service-secret}")
    private String internalSecret;

    /**
     * Creates a new transfer or returns the cached result for an already-processed idempotency key.
     *
     * <p>Idempotency algorithm:
     * <ol>
     *   <li>Check DB for (initiatedBy, idempotencyKey).</li>
     *   <li>If found with same hash → return cached result (I1).</li>
     *   <li>If found with different hash → 409 (I1).</li>
     *   <li>If not found → run the full saga.</li>
     * </ol>
     *
     * @param idempotencyKey client-supplied unique key from the header
     * @param initiatedBy    authenticated user's UUID
     * @param request        the transfer request
     * @return the transfer response (either new or cached)
     */
    @Transactional(noRollbackFor = TransferExecutionException.class)
    public TransferResponse createTransfer(String idempotencyKey, UUID initiatedBy, CreateTransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new TransferValidationException(ErrorCodes.SELF_TRANSFER,
                    "Source and destination accounts must be different");
        }

        String requestHash = hash(toJson(request));

        // ── Idempotency check ──────────────────────────────────────────
        var existing = transferRepository.findByInitiatedByAndIdempotencyKey(initiatedBy, idempotencyKey);
        if (existing.isPresent()) {
            Transfer t = existing.get();
            if (!t.getRequestHash().equals(requestHash)) {
                throw new TransferValidationException(ErrorCodes.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency key already used with a different request payload");
            }
            log.info("Idempotent replay: transferId={} status={}", t.getId(), t.getStatus());
            return TransferResponse.from(t);
        }

        // ── Step 1: Save as PENDING ────────────────────────────────────
        Transfer transfer = Transfer.create(idempotencyKey, requestHash, initiatedBy,
                request.fromAccountId(), request.toAccountId(), request.amount(), request.currency());

        try {
            transferRepository.save(transfer);
        } catch (DataIntegrityViolationException ex) {
            // Race: another request saved the same (initiatedBy, idempotencyKey) concurrently.
            Transfer winner = transferRepository
                    .findByInitiatedByAndIdempotencyKey(initiatedBy, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency conflict but transfer not found"));
            log.info("Concurrent idempotency race resolved: transferId={}", winner.getId());
            return TransferResponse.from(winner);
        }

        // ── Step 2: Apply account balances via Account Service ─────────
        try {
            accountClient.applyTransfer(internalSecret, new ApplyTransferRequest(
                    transfer.getId(),
                    transfer.getFromAccountId(),
                    transfer.getToAccountId(),
                    transfer.getAmount(),
                    transfer.getCurrency()
            ));
        } catch (Exception ex) {
            // Account service failed before any money moved — simple FAILED, no compensation needed.
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason("Account service failed: " + ex.getMessage());
            writeOutboxEvent(transfer, "TransferFailed", initiatedBy);
            transferRepository.save(transfer);
            log.error("Transfer failed at account-apply step: transferId={} reason={}",
                    transfer.getId(), ex.getMessage());
            throw new TransferExecutionException("Transfer failed: " + ex.getMessage(), ex);
        }

        // ── Step 3: Mark DEBITED — committed even if ledger fails ───────
        // This is the critical checkpoint. The SagaRecoveryJob uses this to detect
        // transfers that need compensation if the process crashes here.
        transfer.setStatus(TransferStatus.DEBITED);
        transferRepository.save(transfer);
        log.info("Transfer debited (awaiting ledger): transferId={}", transfer.getId());

        // ── Step 4: Record in Ledger Service ──────────────────────────
        try {
            ledgerClient.recordTransfer(internalSecret, new RecordTransferRequest(
                    transfer.getId(),
                    transfer.getFromAccountId(),
                    transfer.getToAccountId(),
                    transfer.getAmount(),
                    transfer.getCurrency()
            ));

            // ── Step 5: Mark COMPLETED ────────────────────────────────
            transfer.setStatus(TransferStatus.COMPLETED);
            writeOutboxEvent(transfer, "TransferCompleted", initiatedBy);
            transferRepository.save(transfer);
            log.info("Transfer completed: transferId={}", transfer.getId());

        } catch (Exception ledgerEx) {
            // ── Step 6: Compensation — Ledger failed, reverse account balances ──
            log.error("Ledger failed for transferId={}, triggering compensation. reason={}",
                    transfer.getId(), ledgerEx.getMessage());
            triggerCompensation(transfer, initiatedBy, "Ledger service failed: " + ledgerEx.getMessage());
            throw new TransferExecutionException("Transfer failed after ledger error: " + ledgerEx.getMessage(), ledgerEx);
        }

        return TransferResponse.from(transfer);
    }

    /**
     * Executes the compensation step: reverses account balances and marks the transfer FAILED.
     * Called both from {@link #createTransfer} and from {@link SagaRecoveryJob}.
     *
     * @param transfer    the transfer stuck in DEBITED or COMPENSATING state
     * @param initiatedBy the user who initiated the transfer
     * @param reason      human-readable failure reason
     */
    @Transactional(noRollbackFor = Exception.class)
    public void triggerCompensation(Transfer transfer, UUID initiatedBy, String reason) {
        transfer.setStatus(TransferStatus.COMPENSATING);
        transfer.setFailureReason(reason);
        transferRepository.save(transfer);

        try {
            accountClient.reverseTransfer(internalSecret, new ReverseTransferRequest(
                    transfer.getId(),
                    transfer.getFromAccountId(),
                    transfer.getToAccountId(),
                    transfer.getAmount(),
                    transfer.getCurrency()
            ));
            transfer.setStatus(TransferStatus.FAILED);
            writeOutboxEvent(transfer, "TransferFailed", initiatedBy);
            transferRepository.save(transfer);
            log.info("Compensation complete: transferId={}", transfer.getId());

        } catch (Exception compensationEx) {
            // Compensation itself failed — leave in COMPENSATING state so the
            // SagaRecoveryJob retries it on the next sweep.
            log.error("Compensation failed for transferId={}: {}. Will retry via SagaRecoveryJob.",
                    transfer.getId(), compensationEx.getMessage());
            transferRepository.save(transfer);
        }
    }

    /**
     * Gets a transfer by ID, enforcing ownership.
     */
    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID transferId, UUID requestingUserId) {
        Transfer t = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + transferId));
        if (!t.getInitiatedBy().equals(requestingUserId)) {
            throw new TransferNotFoundException("Transfer not found: " + transferId);
        }
        return TransferResponse.from(t);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void writeOutboxEvent(Transfer t, String eventType, UUID initiatedBy) {
        String payload = switch (eventType) {
            case "TransferCompleted" -> toJson(new TransferEventPayload(
                    t.getId(), initiatedBy, t.getFromAccountId(), t.getToAccountId(),
                    t.getAmount(), t.getCurrency()));
            case "TransferFailed" -> toJson(new TransferFailedPayload(
                    t.getId(), initiatedBy, t.getFailureReason()));
            default -> "{}";
        };
        outboxEventRepository.save(OutboxEvent.create("Transfer", t.getId(), eventType, payload));
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize", e);
        }
    }

    record TransferEventPayload(UUID transferId, UUID initiatedBy, UUID fromAccountId, UUID toAccountId,
                                 java.math.BigDecimal amount, String currency) {}
    record TransferFailedPayload(UUID transferId, UUID initiatedBy, String reason) {}
}
