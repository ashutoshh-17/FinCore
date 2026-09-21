package com.bankflow.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Gateway filter that validates the {@code Authorization: Bearer <jwt>} header.
 *
 * <p>On success the filter adds {@code X-User-Id} and {@code X-User-Roles} headers so
 * downstream services can trust the caller identity without re-parsing the token.
 *
 * <p>On failure (missing, expired, tampered) the filter short-circuits with {@code 401}.
 *
 * <p>Note: downstream services ALSO validate the JWT for defense-in-depth (they do NOT
 * trust {@code X-User-Id} headers from untrusted callers). The gateway just provides a
 * first-pass check to avoid unnecessary traffic.
 */
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        super(Config.class);
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("Missing or malformed Authorization header");
                return unauthorized(exchange);
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String roles  = claims.get("roles", String.class);

                // Forward verified identity to downstream services
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.header("X-User-Id", userId)
                                       .header("X-User-Roles", roles != null ? roles : ""))
                        .build();

                return chain.filter(mutated);

            } catch (JwtException ex) {
                log.warn("JWT validation failed: {}", ex.getMessage());
                return unauthorized(exchange);
            }
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = """
                {"status":401,"code":"UNAUTHORIZED","title":"Unauthorized",\
                "detail":"Missing or invalid bearer token"}""".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    public static class Config {
        // No per-route configuration required at this time
    }
}
