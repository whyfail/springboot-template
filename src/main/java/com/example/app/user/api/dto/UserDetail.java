package com.example.app.user.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed user shape; the {@code version} field is the optimistic-lock token clients must echo
 * back on PATCH / PUT requests.
 */
public record UserDetail(
        UUID publicId,
        String username,
        String email,
        String displayName,
        boolean enabled,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt,
        String avatarUrl,
        Instant lastLoginAt,
        long version) {}
