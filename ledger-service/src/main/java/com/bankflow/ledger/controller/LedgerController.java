package com.bankflow.ledger.controller;

import com.bankflow.ledger.dto.LedgerEntryResponse;
import com.bankflow.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Public-facing ledger API.
 *
 * <p>GET /api/v1/accounts/{accountId}/statement — paginated account statement.
 * GET /api/v1/admin/ledger/balance/{accountId}  — computed ledger balance for reconciliation.
 */
@RestController
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * Returns a paginated account statement showing all ledger entries for an account.
     * The caller is responsible for authorisation (Gateway enforces JWT; service
     * does not validate account ownership here — add that in Phase 4 if needed).
     */
    @GetMapping("/api/v1/accounts/{accountId}/statement")
    public ResponseEntity<Page<LedgerEntryResponse>> getStatement(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ledgerService.getStatement(accountId, pageable));
    }

    /**
     * Admin endpoint: returns the ledger-computed balance for an account.
     * Used by the reconciliation process to compare against account-service balances.
     */
    @GetMapping("/api/v1/admin/ledger/balance/{accountId}")
    public ResponseEntity<Map<String, Object>> getLedgerBalance(@PathVariable UUID accountId) {
        BigDecimal balance = ledgerService.computeLedgerBalance(accountId);
        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "ledgerBalance", balance
        ));
    }
}
