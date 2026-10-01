package com.bankflow.ledger.controller;

import com.bankflow.ledger.dto.RecordTransferRequest;
import com.bankflow.ledger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal REST API — callable ONLY by the Transaction service.
 *
 * <p>NOT routed through the Gateway. Protected by {@code X-Internal-Secret} header
 * checked in {@link com.bankflow.ledger.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/internal/ledger")
@RequiredArgsConstructor
public class InternalLedgerController {

    private final LedgerService ledgerService;

    /**
     * Records a completed transfer as two immutable double-entry ledger rows.
     * POST /internal/ledger/record
     *
     * <p>Idempotent: calling with the same transferId multiple times is safe.
     */
    @PostMapping("/record")
    public ResponseEntity<Void> recordTransfer(
            @Valid @RequestBody RecordTransferRequest request) {
        ledgerService.recordTransfer(request);
        return ResponseEntity.ok().build();
    }
}
