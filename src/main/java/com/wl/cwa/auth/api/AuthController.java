package com.wl.cwa.auth.api;

import com.wl.cwa.auth.api.dto.CurrentUserResponse;
import com.wl.cwa.auth.api.dto.LoginRequest;
import com.wl.cwa.auth.api.dto.LoginResponse;
import com.wl.cwa.auth.application.AuthService;
import com.wl.cwa.auth.application.LoginCommand;
import com.wl.cwa.auth.application.RequestContext;
import com.wl.cwa.shared.error.BusinessException;
import com.wl.cwa.shared.error.ErrorCode;
import com.wl.cwa.shared.error.FieldErrorDto;
import com.wl.cwa.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Controllers stay thin: validation, alias normalisation and delegation
 * to the application service.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    public static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (request.username() == null || request.username().isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "username is required",
                    "用户名必填",
                    List.of(new FieldErrorDto("username", "用户名必填")));
        }
        boolean remember = Boolean.TRUE.equals(request.remember());
        return authService.login(
                new LoginCommand(request.username().trim(), request.password(), remember),
                RequestContext.from(httpRequest));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.currentUser(principal);
    }

    /**
     * Revokes the presented bearer token. Idempotent by contract: repeating the call with the same
     * token returns 204 even though the session is already gone; a call without any token returns
     * 401 via {@link BusinessException}.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader("Authorization");
        String bearerToken = header != null && header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()).trim() : null;
        authService.logout(principal, bearerToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
