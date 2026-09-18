package com.example.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.app.audit.domain.AuditEventEntity;
import com.example.app.audit.domain.AuditEventRepository;
import com.example.app.authorization.domain.PermissionEntity;
import com.example.app.authorization.domain.PermissionRepository;
import com.example.app.authorization.domain.RoleEntity;
import com.example.app.authorization.domain.RolePermissionEntity;
import com.example.app.authorization.domain.RolePermissionRepository;
import com.example.app.authorization.domain.RoleRepository;
import com.example.app.shared.security.SessionData;
import com.example.app.shared.security.SessionStore;
import com.example.app.support.ContainerTestSupport;
import com.example.app.user.domain.UserEntity;
import com.example.app.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end authentication flows against real MySQL and Redis: contract aliases, TTLs, failure
 * anonymity, session revocation, max-session eviction, rate limiting, audit rows and the
 * raw-token-never-stored guarantee.
 */
@AutoConfigureMockMvc
@Transactional
class AuthFlowIT extends ContainerTestSupport {

    private static final String STRONG_PASSWORD = "IT-Password-2026!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private Clock clock;

    @BeforeEach
    void flushRedisAndPrepareRoles() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        RoleEntity admin = roleRepository.findByCode("ADMIN").orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "Administrator", null, true)));
        for (String code : List.of("user:read", "user:write", "user:role:write", "audit:read")) {
            PermissionEntity permission = permissionRepository.findByCode(code).orElseThrow();
            if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(admin.getId(), permission.getId())) {
                rolePermissionRepository.save(new RolePermissionEntity(admin, permission));
            }
        }
    }

    private UserEntity createEnabledUser(String username) {
        RoleEntity admin = roleRepository.findByCode("ADMIN").orElseThrow();
        UserEntity user = new UserEntity(username, username + "@example.com", passwordEncoder.encode(STRONG_PASSWORD), "Display " + username, null, clock.instant());
        user.assignRole(admin, null);
        return userRepository.saveAndFlush(user);
    }

    private String login(String body) throws Exception {
        return login(body, "127.0.0.1");
    }

    private String login(String body, String remoteAddr) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/login")
                        .with(request -> {
                            request.setRemoteAddr(remoteAddr);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        return com.example.app.support.JsonText.extractString(response, "token");
    }

    private String loginAs(String username) throws Exception {
        return login("{\"username\":\"" + username + "\",\"password\":\"" + STRONG_PASSWORD + "\"}");
    }

    private String loginAsFrom(String username, String remoteAddr) throws Exception {
        return login("{\"username\":\"" + username + "\",\"password\":\"" + STRONG_PASSWORD + "\"}", remoteAddr);
    }

    @Test
    void loginWithCanonicalFieldsReturnsTopLevelTokenAndAuthorities() throws Exception {
        createEnabledUser("canonical");

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"canonical\",\"password\":\"" + STRONG_PASSWORD + "\",\"remember\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("canonical"))
                .andExpect(jsonPath("$.user.enabled").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.user.permissions.length()").value(4));
    }

    @Test
    void loginWithRememberFlagBehavesLikeCanonicalRequest() throws Exception {
        createEnabledUser("aliased");

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"aliased\",\"password\":\"" + STRONG_PASSWORD + "\",\"remember\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("aliased"));
    }

    @Test
    void rememberFlagExtendsSessionTtlToThirtyDays() throws Exception {
        createEnabledUser("remembered");
        String token = login("{\"username\":\"remembered\",\"password\":\"" + STRONG_PASSWORD + "\",\"remember\":true}");
        String digest = com.example.app.shared.security.Hashes.sha256Hex(token);

        Long ttl = redis.getExpire("app:session:" + digest);

        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(Duration.ofDays(29).toSeconds(), Duration.ofDays(30).toSeconds());
    }

    @Test
    void plainLoginKeepsEightHourTtl() throws Exception {
        createEnabledUser("plain");
        String token = loginAs("plain");
        String digest = com.example.app.shared.security.Hashes.sha256Hex(token);

        Long ttl = redis.getExpire("app:session:" + digest);

        assertThat(ttl).isBetween(Duration.ofHours(7).toSeconds(), Duration.ofHours(8).toSeconds());
    }

    @Test
    void invalidPasswordAndUnknownUserAreIndistinguishable() throws Exception {
        createEnabledUser("existing");

        String wrongPassword = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"existing\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownUser = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody-here\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(com.example.app.support.JsonText.extractString(wrongPassword, "code"))
                .isEqualTo(com.example.app.support.JsonText.extractString(unknownUser, "code"));
        assertThat(com.example.app.support.JsonText.extractString(wrongPassword, "msg"))
                .isEqualTo(com.example.app.support.JsonText.extractString(unknownUser, "msg"));
        assertThat(com.example.app.support.JsonText.extractString(wrongPassword, "msg")).isEqualTo("账号或密码错误");
    }

    @Test
    void meLogoutMeFlowRevokesImmediately() throws Exception {
        createEnabledUser("flow-user");
        String token = loginAs("flow-user");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("flow-user"))
                .andExpect(jsonPath("$.permissions.length()").value(4));

        mockMvc.perform(post("/api/v1/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Idempotent logout: repeating with the same (already revoked) token still succeeds.
        mockMvc.perform(post("/api/v1/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void rawTokenNeverAppearsInRedisOrIndex() throws Exception {
        createEnabledUser("token-hygiene");
        String token = loginAs("token-hygiene");
        String digest = com.example.app.shared.security.Hashes.sha256Hex(token);

        assertThat(redis.keys("app:session:*")).contains("app:session:" + digest);
        for (String key : redis.keys("app:session:*")) {
            String value = redis.opsForValue().get(key);
            assertThat(value).isNotNull();
            assertThat(value.contains(token)).as("raw token leaked in %s", key).isFalse();
        }
        for (String key : redis.keys("app:user-sessions:*")) {
            Set<String> members = redis.opsForZSet().range(key, 0, -1);
            assertThat(members).isNotNull();
            assertThat(members.contains(token)).isFalse();
            assertThat(members).contains(digest);
        }
    }

    @Test
    void sixthLoginEvictsOldestSession() throws Exception {
        createEnabledUser("multi-session");
        List<String> tokens = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            // Distinct client IPs: the per-IP limit is independent of the per-account limit.
            tokens.add(loginAsFrom("multi-session", "10.1.0." + i));
        }

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.get(0)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.get(5)))
                .andExpect(status().isOk());

        // Exactly maxSessionsPerUser live sessions remain.
        int liveSessions = 0;
        for (String token : tokens) {
            String digest = com.example.app.shared.security.Hashes.sha256Hex(token);
            if (redis.opsForValue().get("app:session:" + digest) != null) {
                liveSessions++;
            }
        }
        assertThat(liveSessions).isEqualTo(5);
    }

    @Test
    void exceedingLoginRateLimitReturns429WithRetryAfter() throws Exception {
        createEnabledUser("rate-limited");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"rate-limited\",\"password\":\"wrong-password-" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rate-limited\",\"password\":\"wrong-password-6\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[0-9]+")))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.msg").value("请求过于频繁，请稍后再试"));
    }

    @Test
    void loginFailuresAreAuditedWithoutPasswordOrRawToken() throws Exception {
        createEnabledUser("audited");

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "it-agent")
                        .content("{\"username\":\"audited\",\"password\":\"bad-guess-99\"}"))
                .andExpect(status().isUnauthorized());

        List<AuditEventEntity> events = auditEventRepository.findAll().stream()
                .filter(event -> event.getDetailsJson() != null && event.getDetailsJson().contains("audited"))
                .toList();
        assertThat(events).hasSize(1);
        AuditEventEntity event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("LOGIN_FAILURE");
        assertThat(event.getResult()).isEqualTo("FAILURE");
        assertThat(event.getClientIpHash()).isNotNull();
        assertThat(event.getDetailsJson()).contains("bad-password");
        assertThat(event.getDetailsJson()).doesNotContain(STRONG_PASSWORD).doesNotContain("bad-guess-99");
        assertThat(event.getRequestId()).isNotBlank();
    }

    @Test
    void successfulLoginsAreAudited() throws Exception {
        createEnabledUser("audit-success");
        loginAs("audit-success");

        List<AuditEventEntity> events = auditEventRepository.findAll().stream()
                .filter(event -> event.getDetailsJson() != null && event.getDetailsJson().contains("audit-success"))
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(events.get(0).getResult()).isEqualTo("SUCCESS");
        assertThat(events.get(0).getActorUserId()).isNotNull();
    }

    @Test
    void expiredSessionIsRejectedAndCleanedUp() throws Exception {
        createEnabledUser("expiring");
        UserEntity user = userRepository.findByUsername("expiring").orElseThrow();
        SessionData expired = new SessionData(
                user.getId(),
                user.getPublicId(),
                user.getUsername(),
                user.getDisplayName(),
                null,
                Set.of("ADMIN"),
                Set.of("user:read"),
                clock.instant().minus(Duration.ofHours(9)),
                clock.instant().minus(Duration.ofHours(1)));
        String digest = com.example.app.shared.security.Hashes.sha256Hex("expired-token-value");
        redis.opsForValue().set("app:session:" + digest, newJson(expired), Duration.ofMinutes(5));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer expired-token-value"))
                .andExpect(status().isUnauthorized());

        assertThat(redis.opsForValue().get("app:session:" + digest)).isNull();
    }

    private String newJson(SessionData session) {
        return new tools.jackson.databind.json.JsonMapper().writeValueAsString(session);
    }

    @Test
    void meWithUnknownTokenReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer garbage-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void disabledUserCannotLoginAndResponseDoesNotRevealIt() throws Exception {
        UserEntity user = createEnabledUser("disabled");
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"disabled\",\"password\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));
    }

    @Test
    void concurrentLoginsShareSessionLimitWithoutBreaking() throws Exception {
        createEnabledUser("concurrent");
        // Rapid logins from distinct IPs exercise the Lua eviction path repeatedly while the
        // per-account limit stays clear thanks to successful logins.
        String first = loginAsFrom("concurrent", "10.2.0.0");
        for (int i = 1; i < 6; i++) {
            loginAsFrom("concurrent", "10.2.0." + i);
        }
        assertThat(redis.opsForValue().get("app:session:" + com.example.app.shared.security.Hashes.sha256Hex(first)))
                .isNull();
    }

}
