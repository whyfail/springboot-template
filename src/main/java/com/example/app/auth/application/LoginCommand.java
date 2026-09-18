package com.example.app.auth.application;

/** Normalised login input; field aliases are resolved by the API layer before this point. */
public record LoginCommand(String username, String password, boolean remember) {}
