package com.wl.cwa.shared.security;

import com.wl.cwa.shared.config.CwaSecurityProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed session storage. Keys follow the data-model baseline:
 *
 * <pre>
 * cwa:session:{sha256(token)}     -> session JSON, TTL equals session expiry
 * cwa:user-sessions:{userId}      -> ZSET of digests scored by creation time
 * </pre>
 *
 * The index ZSET TTL is pinned to the remembered-session TTL so it never expires before the longest
 * living session of the user. Eviction beyond {@code maxSessionsPerUser} happens atomically in Lua.
 */
@Component
public class RedisSessionStore implements SessionStore {

    private static final String SESSION_KEY_PREFIX = "cwa:session:";
    private static final String INDEX_KEY_PREFIX = "cwa:user-sessions:";

    private static final DefaultRedisScript<List> EVICT_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            local max = tonumber(ARGV[3])
            local count = redis.call('ZCARD', KEYS[1])
            local evicted = {}
            while count > max do
              local oldest = redis.call('ZPOPMIN', KEYS[1])
              if oldest[1] ~= nil then
                evicted[#evicted + 1] = oldest[1]
              end
              count = count - 1
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
            return evicted
            """,
            List.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CwaSecurityProperties properties;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper, CwaSecurityProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> create(SessionData session, String tokenDigest, Duration ttl) {
        String sessionKey = SESSION_KEY_PREFIX + tokenDigest;
        String indexKey = INDEX_KEY_PREFIX + session.userId();
        redis.opsForValue().set(sessionKey, objectMapper.writeValueAsString(session), ttl);
        List<String> evicted = redis.execute(
                EVICT_SCRIPT,
                List.of(indexKey),
                Long.toString(session.issuedAt().toEpochMilli()),
                tokenDigest,
                Integer.toString(properties.maxSessionsPerUser()),
                Long.toString(properties.rememberedSessionTtl().toSeconds()));
        List<String> evictedDigests = new ArrayList<>();
        if (evicted != null) {
            for (Object digest : evicted) {
                evictedDigests.add(String.valueOf(digest));
            }
        }
        for (String digest : evictedDigests) {
            redis.delete(SESSION_KEY_PREFIX + digest);
        }
        return evictedDigests;
    }

    @Override
    public Optional<SessionData> load(String tokenDigest) {
        String json = redis.opsForValue().get(SESSION_KEY_PREFIX + tokenDigest);
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(json, SessionData.class));
    }

    @Override
    public void revoke(String tokenDigest) {
        String sessionKey = SESSION_KEY_PREFIX + tokenDigest;
        SessionData session = peekSession(sessionKey);
        redis.delete(sessionKey);
        if (session != null) {
            redis.opsForZSet().remove(INDEX_KEY_PREFIX + session.userId(), tokenDigest);
        }
    }

    @Override
    public void revokeAllForUser(long userId) {
        String indexKey = INDEX_KEY_PREFIX + userId;
        Set<String> digests = redis.opsForZSet().range(indexKey, 0, -1);
        if (digests != null) {
            for (String digest : digests) {
                redis.delete(SESSION_KEY_PREFIX + digest);
            }
        }
        redis.delete(indexKey);
    }

    private SessionData peekSession(String sessionKey) {
        String json = redis.opsForValue().get(sessionKey);
        return json == null ? null : objectMapper.readValue(json, SessionData.class);
    }
}
