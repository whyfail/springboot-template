package com.wl.cwa.user.application.command;

import com.wl.cwa.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;

/** Role replacement; revokes all sessions of the target user because authorities change. */
public record ReplaceRolesCommand(UUID publicId, List<String> roleCodes, long version, AuthenticatedUser actor) {}
