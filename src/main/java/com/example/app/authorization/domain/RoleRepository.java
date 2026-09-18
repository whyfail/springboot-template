package com.example.app.authorization.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<RoleEntity> findByCodeInAndEnabledTrue(List<String> codes);

    @Query("select distinct r.code from UserRoleEntity ur join ur.role r where ur.id.userId = :userId")
    Set<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Query("select distinct r.code from RolePermissionEntity rp join rp.role r where rp.id.permissionId = :permissionId")
    Set<String> findRoleCodesByPermissionId(@Param("permissionId") Long permissionId);
}
