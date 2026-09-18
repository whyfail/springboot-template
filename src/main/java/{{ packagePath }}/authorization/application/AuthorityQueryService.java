package {{ package }}.authorization.application;

import {{ package }}.authorization.domain.PermissionRepository;
import {{ package }}.authorization.domain.RoleRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only authority lookups used at login time and by the admin API. */
@Service
public class AuthorityQueryService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public AuthorityQueryService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> roleCodesForUser(Long userId) {
        return roleRepository.findRoleCodesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<String> permissionCodesForUser(Long userId) {
        return permissionRepository.findPermissionCodesByUserId(userId);
    }
}
