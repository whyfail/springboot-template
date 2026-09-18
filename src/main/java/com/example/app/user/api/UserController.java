package com.example.app.user.api;

import com.example.app.shared.api.PageRequestFactory;
import com.example.app.shared.api.PageResponse;
import com.example.app.shared.security.AuthenticatedUser;
import com.example.app.user.api.dto.CreateUserRequest;
import com.example.app.user.api.dto.ReplaceRolesRequest;
import com.example.app.user.api.dto.SetEnabledRequest;
import com.example.app.user.api.dto.UpdateUserRequest;
import com.example.app.user.api.dto.UserDetail;
import com.example.app.user.api.dto.UserListQuery;
import com.example.app.user.api.dto.UserSummary;
import com.example.app.user.application.UserAdministrationService;
import com.example.app.user.application.command.CreateUserCommand;
import com.example.app.user.application.command.ReplaceRolesCommand;
import com.example.app.user.application.command.SetEnabledCommand;
import com.example.app.user.application.command.UpdateUserCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration endpoints. Method security uses the exact permission codes seeded by the
 * migration; controllers stay free of business logic and transactions.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAdministrationService userService;
    private final PageRequestFactory pageRequestFactory;

    public UserController(UserAdministrationService userService, PageRequestFactory pageRequestFactory) {
        this.userService = userService;
        this.pageRequestFactory = pageRequestFactory;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public PageResponse<UserSummary> list(@Valid UserListQuery query) {
        return userService.list(
                query.keyword(),
                query.enabled(),
                query.role(),
                pageRequestFactory.create(query.page(), query.size(), query.sort()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    public ResponseEntity<UserDetail> create(
            @Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal AuthenticatedUser actor) {
        UserDetail created = userService.create(new CreateUserCommand(
                request.username(),
                request.password(),
                request.email(),
                request.displayName(),
                request.avatarUrl(),
                request.roleCodes(),
                actor));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.publicId()))
                .body(created);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('user:read')")
    public UserDetail get(@PathVariable UUID publicId) {
        return userService.get(publicId);
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize("hasAuthority('user:write')")
    public UserDetail update(
            @PathVariable UUID publicId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.update(new UpdateUserCommand(
                publicId, request.email(), request.displayName(), request.avatarUrl(), request.version(), actor));
    }

    @PutMapping("/{publicId}/roles")
    @PreAuthorize("hasAuthority('user:role:write')")
    public UserDetail replaceRoles(
            @PathVariable UUID publicId,
            @Valid @RequestBody ReplaceRolesRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.replaceRoles(new ReplaceRolesCommand(publicId, request.roleCodes(), request.version(), actor));
    }

    @PutMapping("/{publicId}/enabled")
    @PreAuthorize("hasAuthority('user:write')")
    public UserDetail setEnabled(
            @PathVariable UUID publicId,
            @Valid @RequestBody SetEnabledRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.setEnabled(new SetEnabledCommand(publicId, request.enabled(), request.version(), actor));
    }
}
