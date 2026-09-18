package com.example.app.user.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Enable/disable switch; requires the optimistic-lock version. */
public record SetEnabledRequest(
        @NotNull(message = "enabled 必填") Boolean enabled,
        @NotNull(message = "version 必填") @PositiveOrZero(message = "version 不能为负") Long version) {}
