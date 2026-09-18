package {{ package }}.shared.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Production {@link TokenGenerator} backed by {@link SecureRandom}; 256-bit tokens, Base64 URL without padding. */
@Component
public class SecureRandomTokenGenerator implements TokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
