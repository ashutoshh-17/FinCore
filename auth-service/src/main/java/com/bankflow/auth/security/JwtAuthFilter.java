package com.bankflow.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT authentication filter for the Auth service.
 *
 * <p>Validates the Bearer token, extracts the user id and roles, and sets the
 * {@link org.springframework.security.core.context.SecurityContext} so that
 * {@code @AuthenticationPrincipal} works in controllers.
 *
 * <p>Defense-in-depth note: the Gateway also validates the JWT; this filter is an
 * independent second check. Services must NOT trust {@code X-User-Id} headers unless
 * they arrive on the internal Docker network without going through user-facing routes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.parseToken(token);
            String userId = claims.getSubject();
            String roles  = claims.get("roles", String.class);

            List<SimpleGrantedAuthority> authorities = (roles == null || roles.isBlank())
                    ? List.of()
                    : Arrays.stream(roles.split(","))
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.strip()))
                            .collect(Collectors.toList());

            // Build a UserDetails-compatible principal using the userId as username
            var principal = org.springframework.security.core.userdetails.User
                    .withUsername(userId)
                    .password("")
                    .authorities(authorities)
                    .build();

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException ex) {
            log.warn("JWT validation failed in auth-service: {}", ex.getMessage());
            // Don't set authentication — Spring Security will deny the request based on filter chain config
        }

        filterChain.doFilter(request, response);
    }
}
