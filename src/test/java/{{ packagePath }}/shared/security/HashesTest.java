package {{ package }}.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class HashesTest {

    @Test
    void sha256HexMatchesReferenceVector() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expected = HexFormat.of().formatHex(digest.digest("template".getBytes(StandardCharsets.UTF_8)));

        assertThat(Hashes.sha256Hex("template")).isEqualTo(expected).hasSize(64);
    }

    @Test
    void sha256Produces32Bytes() {
        assertThat(Hashes.sha256("token")).hasSize(32);
    }
}
