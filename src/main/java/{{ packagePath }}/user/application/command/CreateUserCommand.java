package {{ package }}.user.application.command;

import {{ package }}.shared.security.AuthenticatedUser;
import java.util.List;

/** Data for creating a user; carries the acting administrator for the audit trail. */
public record CreateUserCommand(
        String username,
        String password,
        String email,
        String displayName,
        String avatarUrl,
        List<String> roleCodes,
        AuthenticatedUser actor) {}
