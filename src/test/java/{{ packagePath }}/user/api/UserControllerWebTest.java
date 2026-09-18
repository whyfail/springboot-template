package {{ package }}.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import {{ package }}.auth.infrastructure.security.SecurityConfig;
import {{ package }}.shared.api.PageResponse;
import {{ package }}.shared.config.SecurityProperties;
import {{ package }}.shared.config.SharedConfig;
import {{ package }}.shared.error.GlobalExceptionHandler;
import {{ package }}.shared.error.ProblemDetailFactory;
import {{ package }}.shared.security.AuthenticatedUser;
import {{ package }}.shared.security.SessionStore;
import {{ package }}.user.application.UserAdministrationService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-contract slice tests for the user admin API: permission boundaries (401/403), Bean
 * Validation, pagination binding and Problem Details shapes.
 */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, SharedConfig.class, ProblemDetailFactory.class, GlobalExceptionHandler.class, {{ package }}.shared.api.PageRequestFactory.class})
@EnableConfigurationProperties(SecurityProperties.class)
class UserControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAdministrationService userService;

    @MockitoBean
    private SessionStore sessionStore;

    private AuthenticatedUser principalWith(String... permissions) {
        return new AuthenticatedUser(
                1L,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "root",
                "Root",
                null,
                Set.of("ADMIN"),
                Set.of(permissions),
                "digest",
                Instant.now(),
                Instant.now().plusSeconds(3600));
    }

    private Authentication asPrincipal(String... permissions) {
        AuthenticatedUser user = principalWith(permissions);
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    }

    @Test
    void listWithoutTokenReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void listWithoutReadPermissionReturns403Problem() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(authentication(asPrincipal("user:write"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"))
                .andExpect(jsonPath("$.msg").value("权限不足"));
    }

    @Test
    void listWithReadPermissionReturnsPageShape() throws Exception {
        when(userService.list(any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(
                        List.of(new {{ package }}.user.api.dto.UserSummary(
                                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                "jane",
                                "jane@example.com",
                                "Jane",
                                true,
                                List.of("ADMIN"),
                                Instant.parse("2026-09-18T09:00:00Z"),
                                Instant.parse("2026-09-18T09:00:00Z"))),
                        0,
                        20,
                        1,
                        1));

        mockMvc.perform(get("/api/v1/users").with(authentication(asPrincipal("user:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].publicId").value("00000000-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void createRequiresWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(authentication(asPrincipal("user:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane\",\"password\":\"LongEnough123!\",\"displayName\":\"Jane\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithShortPasswordFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(authentication(asPrincipal("user:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jane\",\"password\":\"short12\",\"displayName\":\"Jane\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void createWithInvalidUsernamePatternFailsValidation() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(authentication(asPrincipal("user:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bad name!\",\"password\":\"LongEnough123!\",\"displayName\":\"Jane\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void patchWithStaleVersionReturns409FromService() throws Exception {
        when(userService.update(any()))
                .thenThrow(new {{ package }}.shared.error.BusinessException(
                        {{ package }}.shared.error.ErrorCode.VERSION_CONFLICT, "version mismatch", null, List.of()));

        mockMvc.perform(patch("/api/v1/users/" + UUID.randomUUID())
                        .with(authentication(asPrincipal("user:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"New\",\"version\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void replaceRolesRequiresRoleWritePermission() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + UUID.randomUUID() + "/roles")
                        .with(authentication(asPrincipal("user:write", "user:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"ADMIN\"],\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listSizeOverContractMaximumFailsValidation() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("size", "101").with(authentication(asPrincipal("user:read"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void listWithMalformedRolePatternFailsValidation() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("role", "bad-role").with(authentication(asPrincipal("user:read"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
    }
}
