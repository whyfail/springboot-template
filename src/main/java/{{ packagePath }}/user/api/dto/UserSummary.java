package {{ package }}.user.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** External user shape for paginated listings; role codes are sorted for stable output. */
public record UserSummary(
        UUID publicId,
        String username,
        String email,
        String displayName,
        boolean enabled,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt) {}
