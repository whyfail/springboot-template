package com.example.app.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.example.app.shared.config.SecurityProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private static final SecurityProperties PROPERTIES = new SecurityProperties(
            Duration.ofHours(8),
            Duration.ofDays(30),
            5,
            12,
            5,
            20,
            List.of("http://localhost:5173"),
            "https://docs.example.com/problems",
            false);

    @Mock
    private StringRedisTemplate redis;

    @Test
    @SuppressWarnings("unchecked")
    void allowsAttemptWhenCountersBelowLimit() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L);
        LoginRateLimiter limiter = new LoginRateLimiter(redis, PROPERTIES);

        assertThat(limiter.consume("ip", "1.2.3.4")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsAttemptWithRetryAfterWhenMinuteWindowExceeded() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(37L);
        LoginRateLimiter limiter = new LoginRateLimiter(redis, PROPERTIES);

        Optional<Duration> retryAfter = limiter.consume("user", "admin");

        assertThat(retryAfter).contains(Duration.ofSeconds(37));
    }

    @Test
    void clearRemovesBothWindowKeys() {
        LoginRateLimiter limiter = new LoginRateLimiter(redis, PROPERTIES);

        limiter.clear("user", "admin");

        org.mockito.Mockito.verify(redis)
                .delete(org.mockito.ArgumentMatchers.<java.util.Collection<String>>any());
    }
}
