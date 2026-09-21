package com.bankflow.account.controller;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.OpenAccountRequest;
import com.bankflow.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Public REST API for account management.
 * All endpoints require a valid JWT (enforced by {@link com.bankflow.account.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Open a new account for the authenticated user.
     * POST /api/v1/accounts
     */
    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(
            @Valid @RequestBody OpenAccountRequest request,
            Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        AccountResponse response = accountService.openAccount(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all accounts for the authenticated user.
     * GET /api/v1/accounts
     */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts(Authentication authentication) {
        UUID ownerId = extractUserId(authentication);
        return ResponseEntity.ok(accountService.listAccounts(ownerId));
    }

    /**
     * Get details for a specific account.
     * GET /api/v1/accounts/{accountId}
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable UUID accountId,
            Authentication authentication) {
        UUID requestingUserId = extractUserId(authentication);
        return ResponseEntity.ok(accountService.getAccount(accountId, requestingUserId));
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Extracts the user's UUID from the JWT principal.
     * The Gateway forwards the authenticated user id in the {@code X-User-Id} header,
     * and the JwtAuthFilter populates the authentication name with the user id.
     */
    private UUID extractUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
