package com.example.app.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.app.audit.domain.AuditEventEntity;
import com.example.app.audit.domain.AuditEventRepository;
import com.example.app.authorization.domain.PermissionEntity;
import com.example.app.authorization.domain.PermissionRepository;
import com.example.app.authorization.domain.RoleEntity;
import com.example.app.authorization.domain.RolePermissionEntity;
import com.example.app.authorization.domain.RolePermissionRepository;
import com.example.app.authorization.domain.RoleRepository;
import com.example.app.support.ContainerTestSupport;
import com.example.app.user.domain.UserEntity;
import com.example.app.user.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-stack user administration flows with real MySQL and Redis: the ADMIN permission chain,
 * pagination with filters, optimistic locking, uniqueness conflicts, session revocation on
 * disable/role-change and audit rows.
 */
@AutoConfigureMockMvc
@Transactional
class UserAdminIT extends ContainerTestSupport {

    private static final String ADMIN_PASSWORD = "IT-Password-2026!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        roleRepository.findByCode("VIEWER").orElseGet(() -> roleRepository.save(new RoleEntity("VIEWER", "Viewer", null, false)));
    }

    private UserEntity createDbUser(String username, String password) {
        RoleEntity admin = roleRepository
                .findByCode("ADMIN")
                .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "Administrator", null, true)));
        for (String code : List.of("user:read", "user:write", "user:role:write", "audit:read")) {
            PermissionEntity permission = permissionRepository.findByCode(code).orElseThrow();
            if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(admin.getId(), permission.getId())) {
                rolePermissionRepository.save(new RolePermissionEntity(admin, permission));
            }
        }
        UserEntity user = new UserEntity(username, username + "@example.com", passwordEncoder.encode(password), "User " + username, null, clock.instant());
        user.assignRole(admin, null);
        return userRepository.saveAndFlush(user);
    }

    private String login(String username, String password, String remoteAddr) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/login")
                        .with(request -> {
                            request.setRemoteAddr(remoteAddr);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.example.app.support.JsonText.extractString(result.getResponse().getContentAsString(), "token");
    }

    private String createViaApi(String token, String username) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Target-Pass-2026!\","
                                + "\"email\":\"" + username + "@example.com\",\"displayName\":\"User " + username + "\","
                                + "\"roleCodes\":[\"VIEWER\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return com.example.app.support.JsonText.extractString(body, "publicId");
    }

    @Test
    void createListDetailPatchRolesDisableLifecycle() throws Exception {
        createDbUser("chief", ADMIN_PASSWORD);
        String adminToken = login("chief", ADMIN_PASSWORD, "10.9.0.7");

        // CREATE: 201 + Location + body with version 0
        MvcResult created = mockMvc
                .perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"target1\",\"password\":\"Target-Pass-2026!\","
                                + "\"email\":\"target1@example.com\",\"displayName\":\"User target1\","
                                + "\"roleCodes\":[\"VIEWER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("target1"))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String publicId = com.example.app.support.JsonText.extractString(created.getResponse().getContentAsString(), "publicId");
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/users/" + publicId);

        mockMvc.perform(get("/api/v1/users/" + publicId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("target1"));

        // LIST with filters, pagination and sorting
        createViaApi(adminToken, "alpha");
        createViaApi(adminToken, "zulu");
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "target")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "username,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].username").value("target1"));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "VIEWER")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));

        // PATCH with optimistic locking
        MvcResult detailResult = mockMvc
                .perform(get("/api/v1/users/" + publicId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        int version = com.example.app.support.JsonText.extractInt(detailResult.getResponse().getContentAsString(), "version");

        mockMvc.perform(patch("/api/v1/users/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Renamed\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed"))
                .andExpect(jsonPath("$.version").value(version + 1));

        mockMvc.perform(patch("/api/v1/users/" + publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Too Late\",\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // ROLES replace revokes the target's live session
        String targetToken = login("target1", "Target-Pass-2026!", "10.9.0.8");
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk());
        MvcResult rolesResult = mockMvc
                .perform(put("/api/v1/users/" + publicId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[],\"version\":" + (version + 1) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andReturn();
        int versionAfterRoles =
                com.example.app.support.JsonText.extractInt(rolesResult.getResponse().getContentAsString(), "version");
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());

        // DISABLE revokes sessions too
        String targetToken2 = login("target1", "Target-Pass-2026!", "10.9.0.9");
        mockMvc.perform(put("/api/v1/users/" + publicId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"version\":" + versionAfterRoles + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + targetToken2))
                .andExpect(status().isUnauthorized());

        // audit trail for the whole lifecycle
        List<AuditEventEntity> events = auditEventRepository.findAll();
        assertThat(events.stream().map(AuditEventEntity::getEventType))
                .contains("USER_CREATED", "USER_UPDATED", "USER_ROLES_CHANGED", "USER_DISABLED");
    }

    @Test
    void duplicateUsernameAndEmailReturnStable409Codes() throws Exception {
        createDbUser("chief2", ADMIN_PASSWORD);
        String adminToken = login("chief2", ADMIN_PASSWORD, "10.9.0.7");
        createViaApi(adminToken, "dup-user");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dup-user\",\"password\":\"Target-Pass-2026!\",\"displayName\":\"Dup\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other-user\",\"password\":\"Target-Pass-2026!\","
                                + "\"email\":\"dup-user@example.com\",\"displayName\":\"Other\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void unknownRoleOnCreateReturns400WithFieldErrors() throws Exception {
        createDbUser("chief3", ADMIN_PASSWORD);
        String adminToken = login("chief3", ADMIN_PASSWORD, "10.9.0.7");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghosty\",\"password\":\"Target-Pass-2026!\","
                                + "\"displayName\":\"Ghost\",\"roleCodes\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].field").value("roleCodes"));
    }

    @Test
    void patchWithDuplicateEmailConflicts() throws Exception {
        createDbUser("chief4", ADMIN_PASSWORD);
        String adminToken = login("chief4", ADMIN_PASSWORD, "10.9.0.7");
        String idA = createViaApi(adminToken, "user-a");
        createViaApi(adminToken, "user-b");

        mockMvc.perform(patch("/api/v1/users/" + idA)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user-b@example.com\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void selfDisableIsRejected() throws Exception {
        createDbUser("chief5", ADMIN_PASSWORD);
        String adminToken = login("chief5", ADMIN_PASSWORD, "10.9.0.7");
        UUID chiefId = userRepository.findByUsername("chief5").orElseThrow().getPublicId();

        mockMvc.perform(put("/api/v1/users/" + chiefId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_SELF_DISABLE"));
    }

    @Test
    void permissionBoundariesInsideRealSession() throws Exception {
        // reader has only user:read; write endpoints must answer 403 inside a real session.
        RoleEntity readerRole = roleRepository.save(new RoleEntity("READER", "Reader", null, false));
        PermissionEntity read = permissionRepository.findByCode("user:read").orElseThrow();
        rolePermissionRepository.save(new RolePermissionEntity(readerRole, read));
        UserEntity reader = new UserEntity("reader", "reader@example.com", passwordEncoder.encode(ADMIN_PASSWORD), "Reader", null, clock.instant());
        reader.assignRole(readerRole, null);
        userRepository.saveAndFlush(reader);

        String readerToken = login("reader", ADMIN_PASSWORD, "10.9.0.10");

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nope\",\"password\":\"Target-Pass-2026!\",\"displayName\":\"Nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        mockMvc.perform(put("/api/v1/users/" + UUID.randomUUID() + "/roles")
                        .header("Authorization", "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[],\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownUserAndMalformedIdsAnswer404And400() throws Exception {
        createDbUser("chief6", ADMIN_PASSWORD);
        String adminToken = login("chief6", ADMIN_PASSWORD, "10.9.0.7");

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/users/not-a-uuid").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
