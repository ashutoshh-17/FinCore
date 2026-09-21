package com.bankflow.transaction.client.dto;

import java.util.UUID;

/** Mirrors account-service's ReverseTransferRequest DTO — sent over Feign. */
public record ReverseTransferRequest(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId
) {}
