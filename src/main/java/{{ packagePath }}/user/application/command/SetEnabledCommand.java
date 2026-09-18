package {{ package }}.user.application.command;

import {{ package }}.shared.security.AuthenticatedUser;
import java.util.UUID;

/** Enable/disable switch; disabling additionally revokes all sessions of the target user. */
public record SetEnabledCommand(UUID publicId, boolean enabled, long version, AuthenticatedUser actor) {}
