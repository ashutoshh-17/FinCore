package com.bankflow.auth.modules.auth.dto;

import com.bankflow.auth.modules.user.KycStatus;
import com.bankflow.auth.modules.user.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Response for {@code GET /api/v1/users/me}. */
public record UserProfileResponse(
        UUID       userId,
        String     email,
        String     fullName,
        UserStatus status,
        KycStatus  kycStatus,
        Set<String> roles,
        Instant    createdAt
) {}
