package com.wl.cwa.shared.security;

import com.wl.cwa.shared.config.CwaSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Atomic sliding-window login rate limiter backed by Redis. Keys are bucketed per minute and per
 * hour with the subject pseudonymised (SHA-256), so raw IPs and usernames never appear in Redis.
 * Counting and TTL setup happen inside a single Lua script: no read-then-write race window.
 */
@Component
public class LoginRateLimiter {

    public static final String KIND_IP = "ip";
    public static final String KIND_USER = "user";

    private static final String KEY_PREFIX = "cwa:login-rate:";
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local minute = redis.call('INCR', KEYS[1])
            if minute == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
            local hour = redis.call('INCR', KEYS[2])
            if hour == 1 then redis.call('EXPIRE', KEYS[2], tonumber(ARGV[2])) end
            if minute > tonumber(ARGV[3]) then return redis.call('TTL', KEYS[1]) end
            if hour > tonumber(ARGV[4]) then return redis.call('TTL', KEYS[2]) end
            return 0
            """,
            Long.class);

    private final StringRedisTemplate redis;
    private final CwaSecurityProperties properties;

    public LoginRateLimiter(StringRedisTemplate redis, CwaSecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * Records one attempt for the subject of the given kind.
     *
     * @return empty when the attempt is allowed, otherwise the remaining window to wait
     */
    public Optional<Duration> consume(String kind, String subject) {
        Instant now = Instant.now();
        long minuteBucket = now.toEpochMilli() / MINUTE.toMillis();
        long hourBucket = now.toEpochMilli() / HOUR.toMillis();
        String minuteKey = KEY_PREFIX + kind + ":" + Hashes.sha256Hex(kind + ":m:" + subject) + ":" + minuteBucket;
        String hourKey = KEY_PREFIX + kind + ":" + Hashes.sha256Hex(kind + ":h:" + subject) + ":" + hourBucket;
        Long result = redis.execute(
                CONSUME_SCRIPT,
                List.of(minuteKey, hourKey),
                Long.toString(MINUTE.toSeconds()),
                Long.toString(HOUR.toSeconds()),
                Integer.toString(properties.loginAttemptsPerMinute()),
                Integer.toString(properties.loginAttemptsPerHour()));
        if (result == null || result <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(Math.min(result, HOUR.toSeconds())));
    }

    /** Clears the attempt counters after a successful login (audit records are kept). */
    public void clear(String kind, String subject) {
        Instant now = Instant.now();
        long minuteBucket = now.toEpochMilli() / MINUTE.toMillis();
        long hourBucket = now.toEpochMilli() / HOUR.toMillis();
        redis.delete(java.util.List.of(
                KEY_PREFIX + kind + ":" + Hashes.sha256Hex(kind + ":m:" + subject) + ":" + minuteBucket,
                KEY_PREFIX + kind + ":" + Hashes.sha256Hex(kind + ":h:" + subject) + ":" + hourBucket));
    }
}
