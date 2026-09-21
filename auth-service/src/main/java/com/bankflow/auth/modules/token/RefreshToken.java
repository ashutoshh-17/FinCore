package com.bankflow.auth.modules.token;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted refresh token — stored as a BCrypt hash, never in cleartext.
 *
 * <p>Rotation: every successful refresh call revokes the old token and issues a new one
 * (setting {@code revokedAt}). The new token is returned to the client.
 *
 * <p>Expired or revoked tokens are pruned by a scheduled cleanup job.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** FK to {@code users.id} — stored as plain column, no JPA relation needed. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** BCrypt hash of the raw token value sent to the client. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ── Helpers ───────────────────────────────────────────────────

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    // ── Factory ───────────────────────────────────────────────────

    public static RefreshToken create(UUID userId, String tokenHash, long expiryDays) {
        RefreshToken rt = new RefreshToken();
        rt.userId    = userId;
        rt.tokenHash = tokenHash;
        rt.expiresAt = Instant.now().plusSeconds(expiryDays * 24 * 60 * 60);
        return rt;
    }
}
