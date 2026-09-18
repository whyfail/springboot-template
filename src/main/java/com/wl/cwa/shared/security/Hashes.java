package com.wl.cwa.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers used to store token digests and pseudonymised request fingerprints. */
public final class Hashes {

    private Hashes() {}

    public static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String sha256Hex(String input) {
        return HexFormat.of().formatHex(sha256(input));
    }
}
