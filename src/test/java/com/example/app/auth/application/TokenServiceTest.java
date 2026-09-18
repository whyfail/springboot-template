package com.example.app.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.app.shared.security.TokenGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService tokenService = new TokenService(new FixedTokenGenerator());

    @Test
    void delegatesGenerationToSecureGenerator() {
        assertThat(tokenService.generate()).isEqualTo(FixedTokenGenerator.VALUE);
    }

    @Test
    void digestMatchesSha256Hex() {
        String digest = tokenService.digest(FixedTokenGenerator.VALUE);

        // Precomputed SHA-256 of FixedTokenGenerator.VALUE.
        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(tokenService.digest(FixedTokenGenerator.VALUE)).isEqualTo(digest);
        assertThat(tokenService.digest("other-value")).isNotEqualTo(digest);
    }

    /** Deterministic stand-in for the SecureRandom-backed production generator. */
    static class FixedTokenGenerator implements TokenGenerator {

        static final String VALUE = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

        @Override
        public String generate() {
            return VALUE;
        }
    }
}
