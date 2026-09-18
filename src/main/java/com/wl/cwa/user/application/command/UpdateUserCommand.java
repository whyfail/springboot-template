package com.wl.cwa.user.application.command;

import com.wl.cwa.shared.security.AuthenticatedUser;
import java.util.UUID;

/** Profile update; null fields are left unchanged, version is the optimistic-lock token. */
public record UpdateUserCommand(UUID publicId, String email, String displayName, String avatarUrl, long version, AuthenticatedUser actor) {}
