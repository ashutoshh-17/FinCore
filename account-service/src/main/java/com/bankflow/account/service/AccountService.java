package com.bankflow.account.service;

import com.bankflow.account.domain.*;
import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.OpenAccountRequest;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.repository.AuditLogRepository;
import com.bankflow.account.repository.OutboxEventRepository;
import com.bankflow.common.util.MaskingUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for public account operations.
 * Controllers are thin; all domain rules live here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository    accountRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository   auditLogRepository;
    private final ObjectMapper         objectMapper;

    /**
     * Opens a new account for the given user.
     *
     * @param ownerId the authenticated user's UUID (extracted from the JWT by the controller)
     * @param request the account open request
     * @return the created account as a response DTO
     */
    @Transactional
    public AccountResponse openAccount(UUID ownerId, OpenAccountRequest request) {
        String accountNumber = generateAccountNumber();
        Account account = Account.open(ownerId, request.type(), request.currency(), accountNumber);
        accountRepository.save(account);

        // Write outbox event in the same transaction
        outboxEventRepository.save(OutboxEvent.create(
                "Account",
                account.getId(),
                "AccountOpened",
                toJson(new AccountOpenedPayload(account.getId(), ownerId, account.getAccountNumber(), account.getCurrency()))
        ));

        // Audit log
        auditLogRepository.save(AuditLog.of(
                ownerId.toString(), "ACCOUNT_OPENED", "Account", account.getId(),
                toJson(new AccountOpenedPayload(account.getId(), ownerId, account.getAccountNumber(), account.getCurrency())),
                MaskingUtils.currentCorrelationId()
        ));

        log.info("Account opened: accountId={} ownerId={}", account.getId(), ownerId);
        return AccountResponse.from(account);
    }

    /**
     * Lists all accounts for the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(UUID ownerId) {
        return accountRepository.findAllByOwnerId(ownerId)
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    /**
     * Gets a single account, enforcing ownership (I8: no cross-user reads).
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId, UUID requestingUserId) {
        Account account = findOrThrow(accountId);
        enforceOwnership(account, requestingUserId);
        return AccountResponse.from(account);
    }

    // ── Helpers ───────────────────────────────────────────────────

    Account findOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private void enforceOwnership(Account account, UUID requestingUserId) {
        if (!account.getOwnerId().equals(requestingUserId)) {
            // Return 404, not 403, so we don't leak the existence of the account
            throw new AccountNotFoundException("Account not found: " + account.getId());
        }
    }

    private String generateAccountNumber() {
        // Simple deterministic account number — replace with a sequence in production
        return "ACC" + System.currentTimeMillis() % 10_000_000_000L;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    // ── Internal payload records ──────────────────────────────────

    record AccountOpenedPayload(UUID accountId, UUID ownerId, String accountNumber, String currency) {}
}
