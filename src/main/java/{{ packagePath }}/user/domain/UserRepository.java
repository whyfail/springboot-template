package {{ package }}.user.domain;

import {{ package }}.shared.error.BusinessException;
import {{ package }}.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository
        extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    Optional<UserEntity> findWithRolesByPublicId(UUID publicId);

    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    Optional<UserEntity> findWithRolesByUsername(String username);

    default UserEntity getByPublicId(UUID publicId) {
        return findWithRolesByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserEntity u set u.lastLoginAt = :at where u.id = :id")
    int updateLastLogin(@Param("id") Long id, @Param("at") Instant at);

    /** Batched role lookup for page results; avoids N+1 when rendering UserSummary rows. */
    @Query(
            "select new {{ package }}.user.domain.UserRoleRow(u.id, r.code) from UserEntity u "
                    + "join u.roleAssignments ur join ur.role r where u.id in :ids")
    List<UserRoleRow> findRoleRowsForUserIds(@Param("ids") Collection<Long> ids);

    Page<UserEntity> findAll(Specification<UserEntity> specification, Pageable pageable);
}
