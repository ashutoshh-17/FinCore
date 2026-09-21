package com.bankflow.transaction.controller;

import com.bankflow.transaction.dto.CreateTransferRequest;
import com.bankflow.transaction.dto.TransferResponse;
import com.bankflow.transaction.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public transfer endpoints. All require a valid JWT and an {@code Idempotency-Key} header.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /**
     * Initiate a transfer between two accounts.
     * POST /api/v1/transfers
     *
     * <p>Requires header: {@code Idempotency-Key: <client-generated-uuid>}
     */
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request,
            Authentication authentication) {
        UUID initiatedBy = UUID.fromString(authentication.getName());
        TransferResponse response = transferService.createTransfer(idempotencyKey, initiatedBy, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get a specific transfer by ID.
     * GET /api/v1/transfers/{transferId}
     */
    @GetMapping("/transfers/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID transferId,
            Authentication authentication) {
        UUID requestingUserId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(transferService.getTransfer(transferId, requestingUserId));
    }
}
