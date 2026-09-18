package com.example.app.auth.api.dto;

import java.time.Instant;

/** Login result: the token sits at the TOP LEVEL of the body - the four frontends depend on it. */
public record LoginResponse(String token, String tokenType, Instant expiresAt, CurrentUserResponse user) {}
