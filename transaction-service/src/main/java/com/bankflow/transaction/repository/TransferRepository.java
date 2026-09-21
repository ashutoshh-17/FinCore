package com.bankflow.transaction.repository;

import com.bankflow.transaction.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByInitiatedByAndIdempotencyKey(UUID initiatedBy, String idempotencyKey);
}
