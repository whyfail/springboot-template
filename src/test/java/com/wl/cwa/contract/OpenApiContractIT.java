package com.wl.cwa.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wl.cwa.authorization.domain.PermissionEntity;
import com.wl.cwa.authorization.domain.PermissionRepository;
import com.wl.cwa.authorization.domain.RoleEntity;
import com.wl.cwa.authorization.domain.RolePermissionEntity;
import com.wl.cwa.authorization.domain.RolePermissionRepository;
import com.wl.cwa.authorization.domain.RoleRepository;
import com.wl.cwa.support.ContainerTestSupport;
import com.wl.cwa.user.domain.UserEntity;
import com.wl.cwa.user.domain.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

/**
 * Contract gate between {@code openapi.yaml} and the live API: every documented auth/user path and
 * operation must exist, and the login/me/logout/list responses must carry exactly the schema-defined
 * top-level fields, including the CWA extensions (top-level {@code token}, Problem {@code code, msg,
 * requestId, timestamp}).
 */
@AutoConfigureMockMvc
class OpenApiContractIT extends ContainerTestSupport {

    private static final String ADMIN_PASSWORD = "Contract-Pass-2026!";

    private static Map<String, Object> openApi;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private Clock clock;

    @AfterEach
    void cleanupCommittedRows() {
        // No test transaction here: repository calls auto-commit, so remove seeded data explicitly.
        userRepository.findByUsername("contract-admin").ifPresent(userRepository::delete);
    }

    @BeforeAll
    static void loadContract() throws Exception {
        try (var in = Files.newInputStream(Path.of("openapi.yaml"))) {
            openApi = new Yaml().load(in);
        }
        assertThat(openApi).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths() {
        return (Map<String, Object>) openApi.get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) {
        Map<String, Object> components = (Map<String, Object>) openApi.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        return (Map<String, Object>) schemas.get(name);
    }

    @SuppressWarnings("unchecked")
    private Set<String> schemaProperties(String name) {
        Map<String, Object> properties = (Map<String, Object>) schema(name).get("properties");
        return properties == null ? Set.of() : properties.keySet();
    }

    @Test
    void contractDocumentsAllImplementedOperations() {
        Map<String, Object> paths = paths();
        assertThat(paths.keySet()).contains(
                "/api/v1/login",
                "/api/v1/logout",
                "/api/v1/me",
                "/api/v1/users",
                "/api/v1/users/{publicId}",
                "/api/v1/users/{publicId}/roles",
                "/api/v1/users/{publicId}/enabled");
        assertThat(((Map<String, Object>) paths.get("/api/v1/login"))).containsKey("post");
        assertThat(((Map<String, Object>) paths.get("/api/v1/logout"))).containsKey("post");
        assertThat(((Map<String, Object>) paths.get("/api/v1/me"))).containsKey("get");
        assertThat(((Map<String, Object>) paths.get("/api/v1/users"))).containsKeys("get", "post");
        assertThat(((Map<String, Object>) paths.get("/api/v1/users/{publicId}"))).containsKeys("get", "patch");
        assertThat(((Map<String, Object>) paths.get("/api/v1/users/{publicId}/roles"))).containsKey("put");
        assertThat(((Map<String, Object>) paths.get("/api/v1/users/{publicId}/enabled"))).containsKey("put");

        List<String> loginRequired = (List<String>) schema("LoginResponse").get("required");
        assertThat(loginRequired).containsExactly("token", "tokenType", "expiresAt", "user");
        List<String> problemRequired = (List<String>) schema("Problem").get("required");
        assertThat(problemRequired).contains("type", "title", "status", "code", "msg", "requestId", "timestamp");
    }

    @Test
    void liveLoginResponseMatchesLoginResponseSchema() throws Exception {
        prepareAdmin();
        MvcResult result = mockMvc
                .perform(post("/api/v1/login")
                        .with(request -> {
                            request.setRemoteAddr("10.4.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"contract-admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = JsonPath.read(result.getResponse().getContentAsString(), "$");

        assertThat(body.keySet()).isEqualTo(schemaProperties("LoginResponse"));

        Map<String, Object> user = (Map<String, Object>) body.get("user");
        assertThat(user.keySet()).isEqualTo(schemaProperties("CurrentUser"));
    }

    @Test
    void liveMeLogoutAndProblemMatchContract() throws Exception {
        prepareAdmin();
        String token = loginAdmin();

        MvcResult me = mockMvc
                .perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> meBody = JsonPath.read(me.getResponse().getContentAsString(), "$");
        assertThat(meBody.keySet()).isEqualTo(schemaProperties("CurrentUser"));

        MvcResult unauthorized = mockMvc
                .perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        Map<String, Object> problem = JsonPath.read(unauthorized.getResponse().getContentAsString(), "$");
        assertThat(problem.keySet()).containsAll(schemaProperties("Problem").stream()
                .filter(name -> !"detail".equals(name) && !"instance".equals(name) && !"errors".equals(name))
                .toList());
        assertThat(problem.get("code")).isEqualTo("AUTH_REQUIRED");

        MvcResult logout =
                mockMvc.perform(post("/api/v1/logout").header("Authorization", "Bearer " + token))
                        .andExpect(status().isNoContent())
                        .andReturn();
        assertThat(logout.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void liveUserPageMatchesUserPageSchema() throws Exception {
        prepareAdmin();
        String token = loginAdmin();

        MvcResult list = mockMvc
                .perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> page = JsonPath.read(list.getResponse().getContentAsString(), "$");
        assertThat(page.keySet()).isEqualTo(schemaProperties("UserPage"));

        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertThat(items).isNotEmpty();
        assertThat(items.get(0).keySet()).isEqualTo(schemaProperties("UserSummary"));
    }

    private void prepareAdmin() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        RoleEntity admin = roleRepository
                .findByCode("ADMIN")
                .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "Administrator", null, true)));
        for (String code : List.of("user:read", "user:write", "user:role:write", "audit:read")) {
            PermissionEntity permission = permissionRepository.findByCode(code).orElseThrow();
            if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(admin.getId(), permission.getId())) {
                rolePermissionRepository.save(new RolePermissionEntity(admin, permission));
            }
        }
        if (userRepository.findByUsername("contract-admin").isEmpty()) {
            UserEntity adminUser = new UserEntity(
                    "contract-admin", "contract-admin@example.com", passwordEncoder.encode(ADMIN_PASSWORD), "Contract Admin", null, clock.instant());
            adminUser.assignRole(admin, null);
            userRepository.saveAndFlush(adminUser);
        }
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/v1/login")
                        .with(request -> {
                            request.setRemoteAddr("10.4.0.2");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"contract-admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }
}
