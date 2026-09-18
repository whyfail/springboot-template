package {{ package }}.shared.security;

/** Generates cryptographically strong opaque tokens. Tests replace this with a deterministic implementation. */
public interface TokenGenerator {

    /** @return a URL-safe token derived from 32 bytes of secure random data (43 chars, no padding). */
    String generate();
}
