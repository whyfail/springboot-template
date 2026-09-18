package com.wl.cwa.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wl.cwa.support.SharedMySqlOnly;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fail-closed guarantee: when Redis is unreachable, authentication must not degrade to anonymous
 * access. Authenticated paths fail with 503 Problem Details; login fails closed as well; requests
 * without credentials still receive 401.
 */
@AutoConfigureMockMvc
class RedisDownIT extends SharedMySqlOnly {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void deadRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> "redis://127.0.0.1:1");
    }

    @Test
    void meWithBearerTokenFailsClosedWith503() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer some-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SESSION_STORE_UNAVAILABLE"));
    }

    @Test
    void loginFailsClosedWith503InsteadOfBypassingRateLimit() throws Exception {
        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"whatever123\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SESSION_STORE_UNAVAILABLE"));
    }

    @Test
    void requestWithoutCredentialsStillGets401() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
