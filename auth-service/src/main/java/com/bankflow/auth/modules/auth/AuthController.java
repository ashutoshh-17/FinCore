package com.bankflow.auth.modules.auth;

import com.bankflow.auth.modules.auth.dto.*;
import com.bankflow.common.util.MaskingUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Authentication endpoints — all public (no JWT required).
 *
 * <p>Controller is thin: validates input via {@code @Valid}, delegates everything to
 * {@link AuthService}, returns the DTO directly with the right HTTP status.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register, login, refresh and logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request for email={}", MaskingUtils.maskEmail(request.email()));
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Authenticate and receive tokens")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email={}", MaskingUtils.maskEmail(request.email()));
        return authService.login(request);
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke all refresh tokens for the authenticated user")
    public void logout(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        authService.logout(userId);
    }

    @GetMapping("/users/me")
    @Operation(summary = "Get the authenticated user's profile")
    public UserProfileResponse me(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return authService.getProfile(userId);
    }
}
