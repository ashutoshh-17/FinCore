package com.bankflow.transaction.repository;

import com.bankflow.transaction.domain.Transfer;
import com.bankflow.transaction.domain.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByInitiatedByAndIdempotencyKey(UUID initiatedBy, String idempotencyKey);

    /**
     * Finds transfers stuck in a given status for longer than the specified cutoff.
     * Used by the SagaRecoveryJob to detect in-flight transfers that need compensation.
     */
    @Query("SELECT t FROM Transfer t WHERE t.status = :status AND t.updatedAt < :cutoff")
    List<Transfer> findByStatusAndUpdatedAtBefore(
            @Param("status") TransferStatus status,
            @Param("cutoff") Instant cutoff
    );
}
