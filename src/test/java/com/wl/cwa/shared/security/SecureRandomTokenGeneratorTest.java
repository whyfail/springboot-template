package com.wl.cwa.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureRandomTokenGeneratorTest {

    private final SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    void tokensAre43UrlSafeCharsWithoutPadding() {
        String token = generator.generate();

        assertThat(token).hasSize(43).doesNotContain("=", "+", "/");
    }

    @Test
    void tokensAreUnique() {
        Set<String> tokens = Set.of(generator.generate(), generator.generate(), generator.generate());

        assertThat(tokens).hasSize(3);
    }
}
