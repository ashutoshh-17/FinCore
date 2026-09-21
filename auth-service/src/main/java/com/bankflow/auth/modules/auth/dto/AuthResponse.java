package com.bankflow.auth.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Successful auth response returned on register, login and refresh.
 *
 * <p>{@code refreshToken} is omitted from JSON when {@code null} (e.g. after a
 * refresh where the same token may be reused — though in practice we rotate).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(

        UUID   userId,
        String email,
        String accessToken,
        String refreshToken,

        /** Access token lifetime in seconds, for the client to schedule a proactive refresh. */
        long   expiresInSeconds
) {}
