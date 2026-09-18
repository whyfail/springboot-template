package com.example.app.user.application.command;

import com.example.app.shared.security.AuthenticatedUser;
import java.util.UUID;

/** Enable/disable switch; disabling additionally revokes all sessions of the target user. */
public record SetEnabledCommand(UUID publicId, boolean enabled, long version, AuthenticatedUser actor) {}
