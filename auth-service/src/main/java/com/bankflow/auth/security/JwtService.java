package com.bankflow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stateless JWT service — issues and validates access tokens only.
 *
 * <p>Refresh tokens are opaque random strings persisted (hashed) in the database;
 * they are not JWTs.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs
    ) {
        this.signingKey          = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    /**
     * Issues a signed access token for the given user.
     *
     * @param userId user's UUID (becomes JWT {@code sub})
     * @param roles  set of role names to embed in the {@code roles} claim
     * @return signed compact JWT string
     */
    public String generateAccessToken(UUID userId, Set<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
                .claim("roles", String.join(",", roles))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a JWT access token.
     *
     * @param token compact JWT string
     * @return claims if valid
     * @throws io.jsonwebtoken.JwtException if invalid, expired or tampered
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the user id (subject) from a valid token without throwing on expiry.
     * Used only for refresh-token rotation where expiry is handled by the DB record.
     */
    public UUID extractUserId(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.getSubject());
    }
}
