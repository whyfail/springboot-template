package com.example.app.auth.api;

import com.example.app.auth.api.dto.CurrentUserResponse;
import com.example.app.auth.api.dto.LoginRequest;
import com.example.app.auth.api.dto.LoginResponse;
import com.example.app.auth.application.AuthService;
import com.example.app.auth.application.LoginCommand;
import com.example.app.auth.application.RequestContext;
import com.example.app.shared.error.BusinessException;
import com.example.app.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
