package com.example.app.auth.api.dto;

import java.util.Set;
import java.util.UUID;

/** Current-user projection shared by the login response and GET /me. */
public record CurrentUserResponse(
        UUID publicId,
        String username,
        String displayName,
        String avatarUrl,
        boolean enabled,
        Set<String> roles,
        Set<String> permissions) {}
