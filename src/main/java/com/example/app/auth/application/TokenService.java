package com.example.app.auth.application;

import com.example.app.shared.security.Hashes;
import com.example.app.shared.security.TokenGenerator;
import org.springframework.stereotype.Service;

/**
 * Issues opaque bearer tokens and computes their storage digests. The raw token exists only in the
 * login response and the client; everything server-side uses the SHA-256 digest.
 */
@Service
public class TokenService {

    private final TokenGenerator tokenGenerator;

    public TokenService(TokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    /** @return a fresh 256-bit URL-safe opaque token (43 chars). */
    public String generate() {
        return tokenGenerator.generate();
    }

    /** @return lowercase hex SHA-256 digest used as the Redis session key. */
    public String digest(String rawToken) {
        return Hashes.sha256Hex(rawToken);
    }
}
