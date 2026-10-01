package com.bankflow.transaction.client;

import com.bankflow.transaction.client.dto.RecordTransferRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * OpenFeign client for the Ledger Service's internal record endpoint.
 *
 * <p>Called by the Transaction service as Step 2 of the Saga after account balances
 * have been applied (DEBITED state). Failure here triggers compensation.
 *
 * <p>Goes directly to ledger-service on the internal Docker network, NOT through the Gateway.
 */
@FeignClient(name = "ledger-service", url = "${ledger-service.url}")
public interface LedgerClient {

    /**
     * Records the transfer as two immutable double-entry ledger rows.
     * Idempotent by transferId.
     */
    @PostMapping("/internal/ledger/record")
    void recordTransfer(
            @RequestHeader("X-Internal-Secret") String internalSecret,
            @RequestBody RecordTransferRequest request
    );
}
