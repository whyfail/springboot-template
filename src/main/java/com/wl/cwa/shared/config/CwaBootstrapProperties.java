package com.wl.cwa.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * First-administrator bootstrap settings. The bootstrap entry is disabled unless explicitly enabled
 * (local profile or an operator decision), and the password must come from the environment without
 * any default. See {@code user.application.AdminBootstrap}.
 */
@Validated
@ConfigurationProperties(prefix = "cwa.bootstrap")
public record CwaBootstrapProperties(
        boolean enabled, @NotBlank String username, @NotBlank String displayName, String password) {}
