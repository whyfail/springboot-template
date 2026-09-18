package com.wl.cwa.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wl.cwa.audit.application.AuditRecorder;
import com.wl.cwa.audit.domain.AuditEventType;
import com.wl.cwa.audit.domain.AuditResult;
import com.wl.cwa.auth.api.dto.LoginResponse;
import com.wl.cwa.authorization.application.AuthorityQueryService;
import com.wl.cwa.shared.config.CwaSecurityProperties;
import com.wl.cwa.shared.error.BusinessException;
import com.wl.cwa.shared.error.ErrorCode;
import com.wl.cwa.shared.error.RateLimitedException;
import com.wl.cwa.shared.security.AuthenticatedUser;
import com.wl.cwa.shared.security.LoginRateLimiter;
import com.wl.cwa.shared.security.SessionData;
import com.wl.cwa.shared.security.SessionStore;
import com.wl.cwa.user.domain.UserEntity;
import com.wl.cwa.user.domain.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final CwaSecurityProperties PROPERTIES = new CwaSecurityProperties(
            Duration.ofHours(8),
            Duration.ofDays(30),
            5,
            12,
            5,
            20,
            List.of("http://localhost:5173"),
            "https://docs.example.com/problems",
            false);

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.9", "unit-test-agent");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private LoginRateLimiter rateLimiter;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuthorityQueryService authorityQueryService;

    private AuthService authService;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                tokenService,
                sessionStore,
                rateLimiter,
                auditRecorder,
                authorityQueryService,
                PROPERTIES,
                clock,
                meterRegistry);
        lenient().when(tokenService.generate()).thenReturn("raw-token-abc");
        lenient().when(tokenService.digest("raw-token-abc")).thenReturn("digest-abc");
        lenient().when(rateLimiter.consume(anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(authorityQueryService.roleCodesForUser(anyLong())).thenReturn(Set.of("ADMIN"));
        lenient().when(authorityQueryService.permissionCodesForUser(anyLong())).thenReturn(List.of("user:read"));
    }

    private UserEntity activeUser() {
        UserEntity user = new UserEntity("admin", "admin@example.com", "{bcrypt}stored", "Administrator", null, NOW);
        // Reflect the persisted identity the repository would return.
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 42L);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "publicId", UUID.fromString("00000000-0000-0000-0000-000000000042"));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "enabled", true);
        return user;
    }

    @Test
    void loginSuccessCreatesSessionWithPlainTtlAndClearsUserCounters() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("plain-password", "{bcrypt}stored")).thenReturn(true);

        LoginResponse response = authService.login(new LoginCommand("admin", "plain-password", false), CONTEXT);

        ArgumentCaptor<SessionData> sessionCaptor = ArgumentCaptor.forClass(SessionData.class);
        verify(sessionStore).create(sessionCaptor.capture(), eq("digest-abc"), eq(Duration.ofHours(8)));
        SessionData session = sessionCaptor.getValue();
        assertThat(session.userId()).isEqualTo(42L);
        assertThat(session.roles()).containsExactly("ADMIN");
        assertThat(session.permissions()).containsExactly("user:read");
        assertThat(session.issuedAt()).isEqualTo(NOW);
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
        assertThat(response.token()).isEqualTo("raw-token-abc");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().username()).isEqualTo("admin");
        assertThat(response.user().permissions()).containsExactly("user:read");
        verify(rateLimiter).clear("user", "admin");
        verify(userRepository).updateLastLogin(42L, NOW);
        verify(auditRecorder)
                .record(eq(AuditEventType.LOGIN_SUCCESS), eq(AuditResult.SUCCESS), any(), any(), any(), any(), any(), any());
        assertThat(meterRegistry.counter("cwa.auth.login", "result", "success").count()).isEqualTo(1.0);
    }

    @Test
    void rememberLoginUsesRememberedTtl() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        authService.login(new LoginCommand("admin", "plain-password", true), CONTEXT);

        verify(sessionStore).create(any(SessionData.class), anyString(), eq(Duration.ofDays(30)));
    }

    @Test
    void unknownUserGetsGenericErrorAndNoSession() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("ghost", "whatever123", false), CONTEXT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_CREDENTIALS);

        verify(sessionStore, never()).create(any(), anyString(), any());
        verify(auditRecorder)
                .record(eq(AuditEventType.LOGIN_FAILURE), eq(AuditResult.FAILURE), eq(null), any(), any(), any(), any(), any());
    }

    @Test
    void wrongPasswordIsRejectedWithoutLeakingExistence() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("wrong-password", "{bcrypt}stored")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("admin", "wrong-password", false), CONTEXT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_CREDENTIALS)
                .hasMessage("Authentication failed");

        verify(sessionStore, never()).create(any(), anyString(), any());
        assertThat(meterRegistry.counter("cwa.auth.login", "result", "failure").count()).isEqualTo(1.0);
    }

    @Test
    void disabledUserIsRejectedWithGenericError() {
        UserEntity disabled = activeUser();
        org.springframework.test.util.ReflectionTestUtils.setField(disabled, "enabled", false);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginCommand("admin", "plain-password", false), CONTEXT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_INVALID_CREDENTIALS);

        verify(sessionStore, never()).create(any(), anyString(), any());
    }

    @Test
    void rateLimitedAttemptsShortCircuitBeforeAuthentication() {
        when(rateLimiter.consume(anyString(), anyString())).thenReturn(Optional.of(Duration.ofSeconds(12)));

        assertThatThrownBy(() -> authService.login(new LoginCommand("admin", "plain-password", false), CONTEXT))
                .isInstanceOf(RateLimitedException.class);

        verify(userRepository, never()).findByUsername(anyString());
        verify(sessionStore, never()).create(any(), anyString(), any());
    }

    @Test
    void redisOutageDuringSessionCreationFailsClosedWith503() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(sessionStore.create(any(), anyString(), any()))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        assertThatThrownBy(() -> authService.login(new LoginCommand("admin", "plain-password", false), CONTEXT))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_STORE_UNAVAILABLE);

        verify(auditRecorder, never())
                .record(eq(AuditEventType.LOGIN_SUCCESS), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void logoutRevokesSessionAndAuditsWhenAuthenticated() {
        AuthenticatedUser principal = new AuthenticatedUser(
                42L,
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                "admin",
                "Administrator",
                null,
                Set.of("ADMIN"),
                Set.of("user:read"),
                "digest-abc",
                NOW,
                NOW.plusSeconds(3600));

        authService.logout(principal, "ignored");

        verify(sessionStore).revoke("digest-abc");
        verify(auditRecorder).record(eq(AuditEventType.LOGOUT), eq(AuditResult.SUCCESS), any(), any(), any(), any(), any(), any());
    }

    @Test
    void logoutWithRevokedTokenStaysIdempotent() {
        authService.logout(null, "stale-token");

        verify(sessionStore).revoke(tokenService.digest("stale-token"));
        verify(auditRecorder, never())
                .record(eq(AuditEventType.LOGOUT), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void logoutWithoutAnyTokenRequiresAuthentication() {
        assertThatThrownBy(() -> authService.logout(null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_REQUIRED);

        verify(sessionStore, never()).revoke(anyString());
    }
}
