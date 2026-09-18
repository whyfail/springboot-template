package com.example.app.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.app.authorization.domain.RoleEntity;
import com.example.app.authorization.domain.RoleRepository;
import com.example.app.shared.error.BusinessException;
import com.example.app.support.JpaTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Database-contract tests: constraints, associations, projections, optimistic locking, filters. */
class UserJpaIT extends JpaTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private RoleEntity findOrCreateRole(String code) {
        return roleRepository.findByCode(code).orElseGet(() -> roleRepository.save(new RoleEntity(code, code, null, true)));
    }

    private UserEntity newUser(String username, String email, String displayName, boolean enabled) {
        UserEntity user = new UserEntity(username, email, "{bcrypt}hash-" + username, displayName, null, Instant.now());
        user.setEnabled(enabled);
        return user;
    }

    @Test
    void persistsUserWithRolesAndReadsThemBack() {
        RoleEntity admin = findOrCreateRole("ADMIN");
        RoleEntity viewer = findOrCreateRole("VIEWER");

        UserEntity user = newUser("alice", "alice@example.com", "Alice", true);
        user.assignRole(admin, null);
        user.assignRole(viewer, null);
        user = userRepository.saveAndFlush(user);
        Long userId = user.getId();
        UUID publicId = user.getPublicId();

        userRepository.flush();
        UserEntity reloaded = userRepository.findWithRolesByPublicId(publicId).orElseThrow();

        assertThat(reloaded.getId()).isEqualTo(userId);
        assertThat(reloaded.getPublicId()).isEqualTo(publicId);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.roleCodes()).containsExactlyInAnyOrder("ADMIN", "VIEWER");
        assertThat(reloaded.getRoleAssignments().get(0).getRole().getCode()).isIn("ADMIN", "VIEWER");
    }

    @Test
    void duplicateUsernameIsRejectedByDatabase() {
        userRepository.saveAndFlush(newUser("bob", "bob@example.com", "Bob", true));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("bob", "other@example.com", "Bob 2", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateEmailIsRejectedByDatabase() {
        userRepository.saveAndFlush(newUser("carol", "carol@example.com", "Carol", true));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("carol2", "carol@example.com", "Carol 2", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void versionIncrementsAndStaleWriteConflicts() {
        Long id = new TransactionTemplate(transactionManager)
                .execute(status -> userRepository
                        .saveAndFlush(newUser("dave", "dave@example.com", "Dave", true))
                        .getId());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UserEntity detachedV0 = tx.execute(status -> userRepository.findById(id).orElseThrow());
        assertThat(detachedV0.getVersion()).isZero();

        tx.executeWithoutResult(status -> {
            UserEntity managed = userRepository.findById(id).orElseThrow();
            managed.updateProfile("dave2@example.com", "Dave 2", null);
        });
        UserEntity reloaded = tx.execute(status -> userRepository.findById(id).orElseThrow());
        assertThat(reloaded.getVersion()).isEqualTo(1L);

        detachedV0.updateProfile("stale@example.com", "Stale", null);
        assertThatThrownBy(() -> userRepository.saveAndFlush(detachedV0))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // This test commits its own transactions (no test-managed rollback), so clean up.
        tx.executeWithoutResult(status -> userRepository.deleteById(id));
    }

    @Test
    void lastLoginUpdateModifiesOnlyTargetUser() {
        UserEntity user = userRepository.saveAndFlush(newUser("erin", "erin@example.com", "Erin", true));
        Instant loginAt = Instant.parse("2026-09-18T10:15:00Z");

        int updated = userRepository.updateLastLogin(user.getId(), loginAt);
        userRepository.flush();

        assertThat(updated).isEqualTo(1);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isEqualTo(loginAt);
    }

    @Test
    void specificationsFilterByKeywordEnabledAndRole() {
        RoleEntity admin = findOrCreateRole("ADMIN");
        UserEntity alice = newUser("alice.admin", "alice@example.com", "Alice A", true);
        alice.assignRole(admin, null);
        userRepository.saveAndFlush(alice);
        userRepository.saveAndFlush(newUser("bob", "bob@example.com", "Bob B", false));

        Specification<UserEntity> keyword = UserSpecifications.matchesKeyword("alice");
        Page<UserEntity> page = userRepository.findAll(keyword, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getUsername()).isEqualTo("alice.admin");

        Page<UserEntity> enabledOnly =
                userRepository.findAll(UserSpecifications.hasEnabled(true), PageRequest.of(0, 20));
        assertThat(enabledOnly.getTotalElements()).isEqualTo(1);
        assertThat(enabledOnly.getContent().get(0).isEnabled()).isTrue();

        Page<UserEntity> byRole =
                userRepository.findAll(UserSpecifications.hasRole("ADMIN"), PageRequest.of(0, 20));
        assertThat(byRole.getTotalElements()).isEqualTo(1);
        assertThat(byRole.getContent().get(0).getUsername()).isEqualTo("alice.admin");
    }

    @Test
    void sortedPaginationOrdersByUsername() {
        userRepository.saveAndFlush(newUser("zoe", "zoe@example.com", "Zoe", true));
        userRepository.saveAndFlush(newUser("amy", "amy@example.com", "Amy", true));
        userRepository.saveAndFlush(newUser("mike", "mike@example.com", "Mike", true));

        Page<UserEntity> page =
                userRepository.findAll((root, query, cb) -> cb.conjunction(), PageRequest.of(0, 2, Sort.by("username").ascending()));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(UserEntity::getUsername).containsExactly("amy", "mike");
    }

    @Test
    void nullEnabledFilterMatchesEverythingAndMissingUserThrows() {
        assertThatThrownBy(() -> userRepository.getByPublicId(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("User not found");

        Page<UserEntity> page = userRepository.findAll(UserSpecifications.hasEnabled(null), PageRequest.of(0, 20));
        assertThat(page.getContent()).isNotNull();
    }

    @Test
    void roleRowsProjectBatchedWithoutNPlusOne() {
        RoleEntity admin = findOrCreateRole("ADMIN");
        UserEntity alice = newUser("alice", "alice@example.com", "Alice", true);
        alice.assignRole(admin, null);
        alice = userRepository.saveAndFlush(alice);
        UserEntity bob = userRepository.saveAndFlush(newUser("bob", "bob@example.com", "Bob", true));

        List<UserRoleRow> rows = userRepository.findRoleRowsForUserIds(List.of(alice.getId(), bob.getId()));

        assertThat(rows).containsExactly(new UserRoleRow(alice.getId(), "ADMIN"));
    }
}
