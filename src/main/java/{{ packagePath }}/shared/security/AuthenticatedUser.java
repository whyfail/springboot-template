package {{ package }}.shared.security;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Authentication principal reconstructed from the Redis session. Carries the token digest so
 * logout can revoke exactly the presented session; the digest must never leave the server.
 */
public record AuthenticatedUser(
        long userId,
        java.util.UUID publicId,
        String username,
        String displayName,
        String avatarUrl,
        Set<String> roles,
        Set<String> permissions,
        String tokenDigest,
        Instant issuedAt,
        Instant expiresAt) {

    public Set<GrantedAuthority> authorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return Set.copyOf(authorities);
    }
}
