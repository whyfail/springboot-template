package com.wl.cwa.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wl.cwa.auth.application.AuthService;
import com.wl.cwa.auth.application.LoginCommand;
import com.wl.cwa.auth.infrastructure.security.SecurityConfig;
import com.wl.cwa.shared.config.CwaSecurityProperties;
import com.wl.cwa.shared.config.SharedConfig;
import com.wl.cwa.shared.error.GlobalExceptionHandler;
import com.wl.cwa.shared.error.ProblemDetailFactory;
import com.wl.cwa.shared.security.AuthenticatedUser;
import com.wl.cwa.shared.security.SessionStore;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP-contract slice tests for auth endpoints: aliases, validation, Problem Details, 401/403. */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, SharedConfig.class, ProblemDetailFactory.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(CwaSecurityProperties.class)
class AuthControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private SessionStore sessionStore;

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
                42L,
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                "admin",
                "Administrator",
                null,
                Set.of("ADMIN"),
                Set.of("user:read", "user:write"),
                "digest",
                Instant.now(),
                Instant.now().plusSeconds(3600));
    }

    private Authentication auth() {
        AuthenticatedUser user = principal();
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    }

    @Test
    void loginWithSpaAliasesReturnsTopLevelToken() throws Exception {
        Instant expiresAt = Instant.parse("2026-09-19T06:00:00Z");
        when(authService.login(any(), any()))
                .thenReturn(new com.wl.cwa.auth.api.dto.LoginResponse(
                        "opaque-token-value",
                        "Bearer",
                        expiresAt,
                        new com.wl.cwa.auth.api.dto.CurrentUserResponse(
                                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                                "admin",
                                "Administrator",
                                null,
                                true,
                                Set.of("ADMIN"),
                                Set.of("user:read"))));

        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"admin\",\"password\":\"ChangeMe123!\",\"checked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("opaque-token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-19T06:00:00Z"))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"));

        ArgumentCaptor<LoginCommand> command = ArgumentCaptor.forClass(LoginCommand.class);
        verify(authService).login(command.capture(), any());
        assertThat(command.getValue().username()).isEqualTo("admin");
        assertThat(command.getValue().remember()).isTrue();
    }

    @Test
    void loginWithoutPasswordFailsValidationWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void loginWithoutUsernameOrAliasFails() throws Exception {
        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.msg").value("用户名必填"));
    }

    @Test
    void loginWithShortUsernameFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void meWithoutTokenReturnsProblemDetails401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void logoutWithoutTokenReturns401() throws Exception {
        org.mockito.Mockito.doThrow(new com.wl.cwa.shared.error.BusinessException(
                        com.wl.cwa.shared.error.ErrorCode.AUTH_REQUIRED))
                .when(authService)
                .logout(null, null);

        mockMvc.perform(post("/api/v1/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void meWithSessionPrincipalReturnsCurrentUserShape() throws Exception {
        when(authService.currentUser(any()))
                .thenReturn(new com.wl.cwa.auth.api.dto.CurrentUserResponse(
                        UUID.fromString("00000000-0000-0000-0000-000000000042"),
                        "admin",
                        "Administrator",
                        null,
                        true,
                        Set.of("ADMIN"),
                        Set.of("user:read")));

        mockMvc.perform(get("/api/v1/me").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("00000000-0000-0000-0000-000000000042"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void logoutWithSessionPrincipalReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/logout").with(authentication(auth())))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(), any());
    }

    @Test
    void unknownPathIsDeniedToAnonymousCallersWith401() throws Exception {
        // Unknown API paths must not reveal their existence to unauthenticated callers.
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void optionsRequestGetsCorsAllowlistHeader() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void disallowedOriginGetsNoCorsGrant() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/login")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
