package com.example.app.authorization.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    Optional<PermissionEntity> findByCode(String code);

    @Query(
            "select distinct p.code from UserRoleEntity ur join ur.role r "
                    + "join RolePermissionEntity rp on rp.id.roleId = r.id "
                    + "join PermissionEntity p on p.id = rp.id.permissionId "
                    + "where ur.id.userId = :userId and r.enabled = true")
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    @Query(
            "select distinct p from RolePermissionEntity rp join rp.permission p "
                    + "where rp.id.roleId = :roleId")
    List<PermissionEntity> findByRoleId(@Param("roleId") Long roleId);
}
