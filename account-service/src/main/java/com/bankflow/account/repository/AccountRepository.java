package com.bankflow.account.repository;

import com.bankflow.account.domain.Account;
import com.bankflow.account.domain.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByOwnerIdAndStatus(UUID ownerId, AccountStatus status);

    List<Account> findAllByOwnerId(UUID ownerId);

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Loads the account with an OPTIMISTIC_FORCE_INCREMENT lock so Hibernate
     * increments the version column even if we only read the account.
     * Used during the apply-transfer path to guarantee that concurrent apply
     * calls always conflict rather than silently overwriting each other.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
