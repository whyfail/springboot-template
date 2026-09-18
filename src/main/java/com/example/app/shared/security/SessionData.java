package com.example.app.shared.security;

import java.time.Instant;
import java.util.Set;

/**
 * Session payload persisted in Redis under {@code app:session:{sha256(token)}}. Raw tokens never
 * appear here or anywhere else; the digest is only used as the Redis key. Authorities are snapshotted
 * at login; permission-sensitive changes revoke all sessions of the user.
 */
public record SessionData(
        long userId,
        java.util.UUID publicId,
        String username,
        String displayName,
        String avatarUrl,
        Set<String> roles,
        Set<String> permissions,
        Instant issuedAt,
        Instant expiresAt) {}
