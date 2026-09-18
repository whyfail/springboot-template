package com.wl.cwa.authorization.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, RolePermissionId> {

    List<RolePermissionEntity> findByIdRoleId(Long roleId);

    boolean existsByIdRoleIdAndIdPermissionId(Long roleId, Long permissionId);
}
