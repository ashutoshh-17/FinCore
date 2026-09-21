package com.bankflow.auth.modules.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Bulk-revoke all tokens for a user on logout or password change. */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllByUserId(UUID userId, Instant now);

    /** Periodic cleanup: delete tokens that expired or were revoked more than N days ago. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :threshold OR rt.revokedAt < :threshold")
    int deleteExpiredBefore(Instant threshold);
}
