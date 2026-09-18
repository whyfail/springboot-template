package com.example.app.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Startup benchmark: verifies that a single password hash completes within an acceptable time so a
 * misconfigured bcrypt strength is caught before production traffic, not under load.
 */
@Component
public class PasswordHashBenchmark implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordHashBenchmark.class);
    private static final long MAX_HASH_MILLIS = 1500;

    private final PasswordEncoder passwordEncoder;

    public PasswordHashBenchmark(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.nanoTime();
        String hash = passwordEncoder.encode("benchmark-password-" + start);
        long millis = (System.nanoTime() - start) / 1_000_000;
        if (!hash.startsWith("{bcrypt}")) {
            log.warn("Password encoder does not produce algorithm-tagged hashes");
        }
        if (millis > MAX_HASH_MILLIS) {
            log.warn("Single password hash took {} ms (threshold {} ms) - review app.security.bcrypt-strength", millis, MAX_HASH_MILLIS);
        } else {
            log.info("Password hash benchmark: {} ms per hash", millis);
        }
    }
}
