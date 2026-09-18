package {{ package }}.user.application.command;

import {{ package }}.shared.security.AuthenticatedUser;
import java.util.UUID;

/** Profile update; null fields are left unchanged, version is the optimistic-lock token. */
public record UpdateUserCommand(UUID publicId, String email, String displayName, String avatarUrl, long version, AuthenticatedUser actor) {}
