package com.wl.cwa.user.application;

import com.wl.cwa.audit.application.AuditRecorder;
import com.wl.cwa.audit.domain.AuditEventType;
import com.wl.cwa.audit.domain.AuditResult;
import com.wl.cwa.authorization.domain.RoleEntity;
import com.wl.cwa.authorization.domain.RoleRepository;
import com.wl.cwa.shared.api.PageResponse;
import com.wl.cwa.shared.error.BusinessException;
import com.wl.cwa.shared.error.ErrorCode;
import com.wl.cwa.shared.error.FieldErrorDto;
import com.wl.cwa.shared.security.AuthenticatedUser;
import com.wl.cwa.shared.security.SessionStore;
import com.wl.cwa.user.api.dto.UserDetail;
import com.wl.cwa.user.api.dto.UserSummary;
import com.wl.cwa.user.application.command.CreateUserCommand;
import com.wl.cwa.user.application.command.ReplaceRolesCommand;
import com.wl.cwa.user.application.command.SetEnabledCommand;
import com.wl.cwa.user.application.command.UpdateUserCommand;
import com.wl.cwa.user.domain.UserEntity;
import com.wl.cwa.user.domain.UserRepository;
import com.wl.cwa.user.domain.UserRoleRow;
import com.wl.cwa.user.domain.UserSpecifications;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration use cases. Transactions live here (not in controllers), uniqueness conflicts
 * map to stable 409 codes, role changes and disables revoke the affected user's sessions, and every
 * write lands in the audit trail within the same transaction.
 */
@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public UserAdministrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            SessionStore sessionStore,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionStore = sessionStore;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(String keyword, Boolean enabled, String role, Pageable pageable) {
        Specification<UserEntity> specification = UserSpecifications.hasEnabled(enabled);
        if (keyword != null && !keyword.isBlank()) {
            specification = specification.and(UserSpecifications.matchesKeyword(keyword.trim()));
        }
        if (role != null && !role.isBlank()) {
            specification = specification.and(UserSpecifications.hasRole(role));
        }
        Page<UserEntity> page = userRepository.findAll(specification, pageable);

        List<Long> ids = page.getContent().stream().map(UserEntity::getId).toList();
        Map<Long, List<String>> rolesByUser = new HashMap<>();
        if (!ids.isEmpty()) {
            for (UserRoleRow row : userRepository.findRoleRowsForUserIds(ids)) {
                rolesByUser.computeIfAbsent(row.userId(), ignored -> new ArrayList<>()).add(row.roleCode());
            }
        }
        List<UserSummary> items = page.getContent().stream()
                .map(user -> UserDtoMapper.toSummary(user, rolesByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return PageResponse.of(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserDetail get(java.util.UUID publicId) {
        return UserDtoMapper.toDetail(userRepository.getByPublicId(publicId));
    }

    @Transactional
    public UserDetail create(CreateUserCommand command) {
        String username = command.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "username already exists", null, List.of());
        }
        if (command.email() != null && userRepository.existsByEmail(command.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "email already exists", null, List.of());
        }
        List<RoleEntity> roles = resolveRoles(command.roleCodes());

        UserEntity user = new UserEntity(
                username,
                command.email(),
                passwordEncoder.encode(command.password()),
                command.displayName(),
                command.avatarUrl(),
                clock.instant());
        roles.forEach(role -> user.assignRole(role, command.actor() != null ? command.actor().userId() : null));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Raced uniqueness check; the database constraint is the authority.
            throw conflictingUser(user);
        }

        auditRecorder.record(
                AuditEventType.USER_CREATED,
                AuditResult.SUCCESS,
                actorOf(command.actor()),
                AuditRecorder.TARGET_USER,
                user.getPublicId(),
                Map.of("username", user.getUsername(), "roles", user.roleCodes()),
                null,
                null);
        return UserDtoMapper.toDetail(user);
    }

    @Transactional
    public UserDetail update(UpdateUserCommand command) {
        UserEntity user = userRepository.getByPublicId(command.publicId());
        assertVersion(user, command.version());
        if (command.email() == null && command.displayName() == null && command.avatarUrl() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "No mutable fields provided",
                    "至少需要提供一个可更新字段",
                    List.of());
        }
        if (command.email() != null && !command.email().equals(user.getEmail()) && userRepository.existsByEmail(command.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "email already exists", null, List.of());
        }
        user.updateProfile(command.email(), command.displayName(), command.avatarUrl());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "conflicting data", null, List.of());
        }

        auditRecorder.record(
                AuditEventType.USER_UPDATED,
                AuditResult.SUCCESS,
                actorOf(command.actor()),
                AuditRecorder.TARGET_USER,
                user.getPublicId(),
                Map.of("username", user.getUsername()),
                null,
                null);
        return UserDtoMapper.toDetail(user);
    }

    @Transactional
    public UserDetail replaceRoles(ReplaceRolesCommand command) {
        UserEntity user = userRepository.getByPublicId(command.publicId());
        assertVersion(user, command.version());
        List<RoleEntity> roles = resolveRoles(command.roleCodes());

        List<String> previousRoles = user.roleCodes();
        user.replaceRoles(roles, command.actor() != null ? command.actor().userId() : null);
        userRepository.saveAndFlush(user);
        // Authorities changed: existing sessions still carry the old permission snapshot.
        sessionStore.revokeAllForUser(user.getId());

        auditRecorder.record(
                AuditEventType.USER_ROLES_CHANGED,
                AuditResult.SUCCESS,
                actorOf(command.actor()),
                AuditRecorder.TARGET_USER,
                user.getPublicId(),
                Map.of("username", user.getUsername(), "previousRoles", previousRoles, "newRoles", user.roleCodes()),
                null,
                null);
        return UserDtoMapper.toDetail(user);
    }

    @Transactional
    public UserDetail setEnabled(SetEnabledCommand command) {
        UserEntity user = userRepository.getByPublicId(command.publicId());
        assertVersion(user, command.version());
        if (!command.enabled() && command.actor() != null && command.actor().publicId().equals(user.getPublicId())) {
            throw new BusinessException(ErrorCode.USER_SELF_DISABLE, "cannot disable own account", null, List.of());
        }
        user.setEnabled(command.enabled());
        userRepository.saveAndFlush(user);
        if (!command.enabled()) {
            sessionStore.revokeAllForUser(user.getId());
        }

        auditRecorder.record(
                command.enabled() ? AuditEventType.USER_ENABLED : AuditEventType.USER_DISABLED,
                AuditResult.SUCCESS,
                actorOf(command.actor()),
                AuditRecorder.TARGET_USER,
                user.getPublicId(),
                Map.of("username", user.getUsername()),
                null,
                null);
        return UserDtoMapper.toDetail(user);
    }

    private void assertVersion(UserEntity user, long expectedVersion) {
        if (user.getVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "version mismatch", null, List.of());
        }
    }

    private List<RoleEntity> resolveRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        List<String> distinct = List.copyOf(new LinkedHashSet<>(roleCodes));
        List<RoleEntity> roles = roleRepository.findByCodeInAndEnabledTrue(distinct);
        if (roles.size() != distinct.size()) {
            Set<String> found = new LinkedHashSet<>(roles.stream().map(RoleEntity::getCode).toList());
            List<String> unknown = distinct.stream().filter(code -> !found.contains(code)).toList();
            throw new BusinessException(
                    ErrorCode.ROLE_NOT_FOUND,
                    "Unknown role codes: " + unknown,
                    "角色不存在",
                    List.of(new FieldErrorDto("roleCodes", "未知角色: " + String.join(", ", unknown))));
        }
        return roles;
    }

    private BusinessException conflictingUser(UserEntity user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "username already exists", null, List.of());
        }
        if (user.getEmail() != null && userRepository.existsByEmail(user.getEmail())) {
            return new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "email already exists", null, List.of());
        }
        return new BusinessException(ErrorCode.DATA_CONFLICT, "conflicting data", null, List.of());
    }

    private AuditRecorder.Actor actorOf(AuthenticatedUser actor) {
        return actor != null ? new AuditRecorder.Actor(actor.userId(), actor.publicId()) : null;
    }
}
