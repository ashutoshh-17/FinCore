package com.bankflow.transaction.service;

import com.bankflow.transaction.client.AccountClient;
import com.bankflow.transaction.client.dto.ApplyTransferRequest;
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
 * Orchestrates the transfer saga.
 *
 * <p>Phase 2 simplified flow (without Ledger):
 * PENDING → call Account apply → COMPLETED or FAILED
 *
 * <p>Phase 3 will add: DEBITED → call Ledger → COMPLETED, with full compensation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransferRepository    transferRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AccountClient         accountClient;
    private final ObjectMapper          objectMapper;

    @Value("${internal.service-secret}")
    private String internalSecret;

    /**
     * Creates a new transfer or returns the cached result for an already-processed idempotency key.
     *
     * <p>Idempotency algorithm:
     * <ol>
     *   <li>Check Redis fast-path (future enhancement — Phase 4).</li>
     *   <li>Query DB for (initiatedBy, idempotencyKey).</li>
     *   <li>If found with same hash → return cached result (I1).</li>
     *   <li>If found with different hash → 409 (I1).</li>
     *   <li>If not found → create PENDING, apply, mark COMPLETED/FAILED.</li>
     * </ol>
     *
     * @param idempotencyKey client-supplied unique key from the header
     * @param initiatedBy    authenticated user's UUID
     * @param request        the transfer request
     * @return the transfer response (either new or cached)
     */
    @Transactional
    public TransferResponse createTransfer(String idempotencyKey, UUID initiatedBy, CreateTransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new TransferValidationException(ErrorCodes.SELF_TRANSFER, "Source and destination accounts must be different");
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
            log.info("Idempotent replay: transferId={} idempotencyKey={}", t.getId(), idempotencyKey);
            return TransferResponse.from(t);
        }

        // ── Create PENDING transfer ───────────────────────────────────
        Transfer transfer = Transfer.create(idempotencyKey, requestHash, initiatedBy,
                request.fromAccountId(), request.toAccountId(), request.amount(), request.currency());

        try {
            transferRepository.save(transfer);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another request saved the same (initiatedBy, idempotencyKey) concurrently.
            // Reload and return the winner's result.
            Transfer winner = transferRepository
                    .findByInitiatedByAndIdempotencyKey(initiatedBy, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotency conflict but transfer not found"));
            log.info("Concurrent idempotency race resolved: transferId={}", winner.getId());
            return TransferResponse.from(winner);
        }

        // ── Apply funds via Account Service (OpenFeign) ───────────────
        try {
            accountClient.applyTransfer(internalSecret, new ApplyTransferRequest(
                    transfer.getId(),
                    transfer.getFromAccountId(),
                    transfer.getToAccountId(),
                    transfer.getAmount(),
                    transfer.getCurrency()
            ));

            // ── Mark COMPLETED + write outbox event (same transaction) ────
            transfer.setStatus(TransferStatus.COMPLETED);
            outboxEventRepository.save(OutboxEvent.create(
                    "Transfer", transfer.getId(), "TransferCompleted",
                    toJson(new TransferEventPayload(transfer.getId(), initiatedBy,
                            transfer.getFromAccountId(), transfer.getToAccountId(),
                            transfer.getAmount(), transfer.getCurrency()))
            ));
            log.info("Transfer completed: transferId={}", transfer.getId());

        } catch (Exception ex) {
            // ── Mark FAILED ───────────────────────────────────────────────
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(ex.getMessage());
            outboxEventRepository.save(OutboxEvent.create(
                    "Transfer", transfer.getId(), "TransferFailed",
                    toJson(new TransferFailedPayload(transfer.getId(), initiatedBy, ex.getMessage()))
            ));
            log.error("Transfer failed: transferId={} reason={}", transfer.getId(), ex.getMessage());
            throw new TransferExecutionException("Transfer failed: " + ex.getMessage(), ex);
        } finally {
            transferRepository.save(transfer);
        }

        return TransferResponse.from(transfer);
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
