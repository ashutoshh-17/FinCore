package com.bankflow.transaction.client;

import com.bankflow.transaction.client.dto.ApplyTransferRequest;
import com.bankflow.transaction.client.dto.ReverseTransferRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * OpenFeign client for the Account Service's internal transfer endpoints.
 *
 * <p>These calls go directly to the account-service on the internal Docker network,
 * NOT through the Gateway. The {@code X-Internal-Secret} header is required by the
 * account-service's InternalSecretFilter.
 *
 * <p>Resilience4j retry and circuit breaker are configured in application.yml
 * under {@code resilience4j.retry.instances.accountServiceApply}.
 */
@FeignClient(name = "account-service", url = "${account-service.url}")
public interface AccountClient {

    /**
     * Atomically debit the source and credit the destination account.
     * Idempotent by transferId.
     */
    @PostMapping("/internal/accounts/transfers/apply")
    void applyTransfer(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody ApplyTransferRequest request
    );

    /**
     * Reverse a previously applied transfer (saga compensation).
     * Idempotent by transferId.
     */
    @PostMapping("/internal/accounts/transfers/reverse")
    void reverseTransfer(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody ReverseTransferRequest request
    );
}
