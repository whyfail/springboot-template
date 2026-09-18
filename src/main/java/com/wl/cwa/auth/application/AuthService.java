package com.wl.cwa.auth.application;

import com.wl.cwa.audit.application.AuditRecorder;
import com.wl.cwa.audit.domain.AuditEventType;
import com.wl.cwa.audit.domain.AuditResult;
import com.wl.cwa.auth.api.dto.CurrentUserResponse;
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
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Authentication use cases: login (with rate limiting, auditing and Redis session creation),
 * current-user projection and logout. The Redis session write happens after the database work and
 * any Redis outage fails closed with a 503 - never as anonymous access.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SessionStore sessionStore;
    private final LoginRateLimiter rateLimiter;
    private final AuditRecorder auditRecorder;
    private final AuthorityQueryService authorityQueryService;
    private final CwaSecurityProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    /** Valid-format dummy hash used to equalise timing for unknown usernames. */
    private final String dummyHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            SessionStore sessionStore,
            LoginRateLimiter rateLimiter,
            AuditRecorder auditRecorder,
            AuthorityQueryService authorityQueryService,
            CwaSecurityProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
        this.rateLimiter = rateLimiter;
        this.auditRecorder = auditRecorder;
        this.authorityQueryService = authorityQueryService;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.dummyHash = passwordEncoder.encode("cwa-timing-equalizer-dummy");
    }

    public LoginResponse login(LoginCommand command, RequestContext context) {
        Instant now = clock.instant();
        rejectIfRateLimited(LoginRateLimiter.KIND_IP, context.clientIp());
        rejectIfRateLimited(LoginRateLimiter.KIND_USER, command.username());

        Optional<UserEntity> found = userRepository.findByUsername(command.username());
        if (found.isEmpty()) {
            // Equalise timing with the known-user failure path before rejecting.
            passwordEncoder.matches(command.password(), dummyHash);
            recordLoginFailure(AuditEventType.LOGIN_FAILURE, null, command.username(), "unknown-user", context);
            throw invalidCredentials();
        }
        UserEntity user = found.get();
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            recordLoginFailure(AuditEventType.LOGIN_FAILURE, user, command.username(), "bad-password", context);
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            recordLoginFailure(AuditEventType.LOGIN_FAILURE, user, command.username(), "disabled", context);
            throw invalidCredentials();
        }

        Set<String> roles = authorityQueryService.roleCodesForUser(user.getId());
        Set<String> permissions = new HashSet<>(authorityQueryService.permissionCodesForUser(user.getId()));
        Duration ttl = command.remember() ? properties.rememberedSessionTtl() : properties.sessionTtl();
        SessionData session = new SessionData(
                user.getId(),
                user.getPublicId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                roles,
                permissions,
                now,
                now.plus(ttl));
        String token = tokenService.generate();
        String digest = tokenService.digest(token);
        try {
            sessionStore.create(session, digest, ttl);
        } catch (DataAccessException e) {
            throw sessionStoreUnavailable();
        }
        userRepository.updateLastLogin(user.getId(), now);
        auditRecorder.record(
                AuditEventType.LOGIN_SUCCESS,
                AuditResult.SUCCESS,
                new AuditRecorder.Actor(user.getId(), user.getPublicId()),
                AuditRecorder.TARGET_USER,
                user.getPublicId(),
                Map.of("username", user.getUsername(), "remember", command.remember()),
                context.clientIp(),
                context.userAgent());
        rateLimiter.clear(LoginRateLimiter.KIND_USER, command.username());
        meterRegistry.counter("cwa.auth.login", "result", "success").increment();

        CurrentUserResponse currentUser = new CurrentUserResponse(
                user.getPublicId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl(), true, roles, permissions);
        return new LoginResponse(token, "Bearer", session.expiresAt(), currentUser);
    }

    public CurrentUserResponse currentUser(AuthenticatedUser principal) {
        return new CurrentUserResponse(
                principal.publicId(),
                principal.username(),
                principal.displayName(),
                principal.avatarUrl(),
                true,
                principal.roles(),
                principal.permissions());
    }

    /** Revokes the presented session. Repeating the call with the same token stays a no-op. */
    public void logout(AuthenticatedUser principal, String bearerToken) {
        if (principal != null) {
            sessionStore.revoke(principal.tokenDigest());
            auditRecorder.record(
                    AuditEventType.LOGOUT,
                    AuditResult.SUCCESS,
                    new AuditRecorder.Actor(principal.userId(), principal.publicId()),
                    AuditRecorder.TARGET_USER,
                    principal.publicId(),
                    Map.of("username", principal.username()),
                    null,
                    null);
            return;
        }
        if (bearerToken != null && !bearerToken.isBlank()) {
            // Token already invalid or revoked: logout stays idempotent and returns success.
            sessionStore.revoke(tokenService.digest(bearerToken));
            return;
        }
        throw new BusinessException(ErrorCode.AUTH_REQUIRED);
    }

    private void rejectIfRateLimited(String kind, String subject) {
        Optional<Duration> retryAfter;
        try {
            retryAfter = rateLimiter.consume(kind, subject);
        } catch (DataAccessException e) {
            throw sessionStoreUnavailable();
        }
        if (retryAfter.isPresent()) {
            meterRegistry.counter("cwa.auth.login", "result", "rate_limited").increment();
            throw new RateLimitedException(retryAfter.get());
        }
    }

    private BusinessException sessionStoreUnavailable() {
        meterRegistry.counter("cwa.auth.login", "result", "session_store_error").increment();
        return new BusinessException(ErrorCode.SESSION_STORE_UNAVAILABLE, "Session store is unavailable", null, List.of());
    }

    private void recordLoginFailure(
            AuditEventType eventType, UserEntity user, String username, String reason, RequestContext context) {
        meterRegistry.counter("cwa.auth.login", "result", "failure").increment();
        auditRecorder.record(
                eventType,
                AuditResult.FAILURE,
                user != null ? new AuditRecorder.Actor(user.getId(), user.getPublicId()) : null,
                AuditRecorder.TARGET_USER,
                user != null ? user.getPublicId() : null,
                Map.of("username", username, "reason", reason),
                context.clientIp(),
                context.userAgent());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Authentication failed", null, List.of());
    }
}
