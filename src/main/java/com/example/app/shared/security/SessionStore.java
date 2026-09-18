package com.example.app.shared.security;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Server-side opaque-token session storage. Implementations store only the SHA-256 digest of the
 * token as the key; they must fail closed (propagate infrastructure errors) when Redis is
 * unavailable so authentication can never degrade to anonymous access.
 */
public interface SessionStore {

    /**
     * Stores a session and indexes it for the user. If the per-user session limit is exceeded, the
     * oldest sessions are evicted and their digests are returned (their keys are already deleted).
     */
    List<String> create(SessionData session, String tokenDigest, Duration ttl);

    Optional<SessionData> load(String tokenDigest);

    /** Idempotent removal of a single session. */
    void revoke(String tokenDigest);

    /** Removes every session of the user; used on disable, password change and role changes. */
    void revokeAllForUser(long userId);
}
