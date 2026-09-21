package com.bankflow.account.controller;

import com.bankflow.account.dto.ApplyTransferRequest;
import com.bankflow.account.dto.ReverseTransferRequest;
import com.bankflow.account.service.InternalTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal REST API — callable ONLY by the Transaction service.
 *
 * <p>These endpoints are NOT exposed through the Gateway (/internal/** is blocked at gateway level).
 * They are additionally protected by the {@code X-Internal-Secret} header check in
 * {@link com.bankflow.account.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/internal/accounts/transfers")
@RequiredArgsConstructor
public class InternalAccountController {

    private final InternalTransferService internalTransferService;

    /**
     * Atomically debit source and credit destination for a given transferId.
     * POST /internal/accounts/transfers/apply
     * Idempotent by transferId.
     */
    @PostMapping("/apply")
    public ResponseEntity<Void> applyTransfer(@Valid @RequestBody ApplyTransferRequest request) {
        internalTransferService.applyTransfer(request);
        return ResponseEntity.ok().build();
    }

    /**
     * Reverse a previously applied transfer (saga compensation).
     * POST /internal/accounts/transfers/reverse
     * Idempotent by transferId.
     */
    @PostMapping("/reverse")
    public ResponseEntity<Void> reverseTransfer(@Valid @RequestBody ReverseTransferRequest request) {
        internalTransferService.reverseTransfer(request);
        return ResponseEntity.ok().build();
    }
}
