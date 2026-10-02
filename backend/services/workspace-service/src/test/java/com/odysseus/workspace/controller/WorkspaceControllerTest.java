package com.odysseus.workspace.controller;

import com.odysseus.workspace.service.WorkspaceService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.odysseus.workspace.dto.WorkspaceResponse;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;

@WebMvcTest(WorkspaceController.class)
class WorkspaceControllerTest extends ApiTestBase {

    @MockitoBean
    private WorkspaceService workspaceService;

    private static final String BODY = "{\"name\":\"Acme\"}";

    private WorkspaceResponse response() {
        return new WorkspaceResponse(WORKSPACE_A, "Acme", Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("POST /workspaces: владелец с подтверждённым email создаёт workspace, email уходит в сервис, 201")
    void create_owner_returns201() throws Exception {
        when(workspaceService.create(any(), eq("user-1"), eq("user@acme.io"))).thenReturn(response());

        mvc.perform(post("/api/v1/workspaces").with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme"));

        verify(workspaceService).create(any(), eq("user-1"), eq("user@acme.io"));
    }

    @Test
    @DisplayName("POST /workspaces: создание работает без подписки (она создаётся вместе с workspace)")
    void create_noSubscription_notBlockedByGuard() throws Exception {
        when(subscriptionExpiryPort.findExpiresAt(any())).thenReturn(Optional.empty());
        when(workspaceService.create(any(), any(), any())).thenReturn(response());

        mvc.perform(post("/api/v1/workspaces").with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /workspaces: email не подтверждён, в сервис уходит null вместо email")
    void create_emailNotVerified_passesNullEmail() throws Exception {
        when(workspaceService.create(any(), eq("user-1"), isNull())).thenReturn(response());

        mvc.perform(post("/api/v1/workspaces").with(tokenOf(WORKSPACE_A, false))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(workspaceService).create(any(), eq("user-1"), isNull());
    }

    @Test
    @DisplayName("POST /workspaces: пустое имя, 400")
    void create_blankName_returns400() throws Exception {
        mvc.perform(post("/api/v1/workspaces").with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("POST /workspaces: первый вход без записи Member регистрирует workspace, создатель становится OWNER")
    void create_noMember_returns201() throws Exception {
        when(workspaceService.create(any(), eq("user-1"), eq("user@acme.io"))).thenReturn(response());

        mvc.perform(post("/api/v1/workspaces").with(token(WORKSPACE_A))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /workspaces/{id}: без записи Member ролей нет, 403")
    void rename_noMember_returns403() throws Exception {
        mvc.perform(put("/api/v1/workspaces/{id}", WORKSPACE_A).with(token(WORKSPACE_A))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("PUT /workspaces/{id}: realm-роль OWNER в токене без записи Member не даёт прав, 403")
    void rename_realmRoleWithoutMember_returns403() throws Exception {
        when(memberRolePort.findRole(WORKSPACE_A, "user-1")).thenReturn(java.util.Optional.empty());

        mvc.perform(put("/api/v1/workspaces/{id}", WORKSPACE_A)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(j -> j.subject("user-1")
                                        .claim("realm_access", java.util.Map.of("roles", List.of("OWNER")))
                                        .claim("organization", java.util.Map.of("acme",
                                                java.util.Map.of("id", WORKSPACE_A.toString()))))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_OWNER")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("POST /workspaces: workspace уже есть, 409")
    void create_duplicate_returns409() throws Exception {
        when(workspaceService.create(any(), any(), any())).thenThrow(new ConflictException("Workspace уже зарегистрирован"));

        mvc.perform(post("/api/v1/workspaces").with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /workspaces: без токена, 401")
    void create_noToken_returns401() throws Exception {
        mvc.perform(post("/api/v1/workspaces").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /workspaces: список со страницей")
    void list_authenticated_returnsPage() throws Exception {
        when(workspaceService.list(any())).thenReturn(new PageImpl<>(List.of(response())));

        mvc.perform(get("/api/v1/workspaces").with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(WORKSPACE_A.toString()));
    }

    @Test
    @DisplayName("GET /workspaces/{id}: чужой или несуществующий, 404")
    void get_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(workspaceService.get(id)).thenThrow(new NotFoundException("Workspace не найден"));

        mvc.perform(get("/api/v1/workspaces/{id}", id).with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /workspaces/{id}: токен без организации, 403")
    void get_noOrganization_returns403() throws Exception {
        mvc.perform(get("/api/v1/workspaces/{id}", WORKSPACE_A)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /workspaces/{id}: ADMIN переименовывает")
    void rename_admin_returns200() throws Exception {
        when(workspaceService.rename(eq(WORKSPACE_A), any())).thenReturn(response());

        mvc.perform(put("/api/v1/workspaces/{id}", WORKSPACE_A).with(token(WORKSPACE_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /workspaces/{id}: MANAGER, 403")
    void rename_manager_returns403() throws Exception {
        mvc.perform(put("/api/v1/workspaces/{id}", WORKSPACE_A).with(token(WORKSPACE_A, "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /workspaces/{id}: подписка истекла, 403 ProblemDetail")
    void rename_expiredSubscription_returns403() throws Exception {
        when(subscriptionExpiryPort.findExpiresAt(any())).thenReturn(Optional.of(Instant.now().minusSeconds(1)));

        mvc.perform(put("/api/v1/workspaces/{id}", WORKSPACE_A).with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("GET /workspaces: без записи Member, 403 ProblemDetail")
    void list_noMember_returns403() throws Exception {
        mvc.perform(get("/api/v1/workspaces").with(token(WORKSPACE_A)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("GET /workspaces/{id}: без записи Member, 403 ProblemDetail")
    void get_noMember_returns403() throws Exception {
        mvc.perform(get("/api/v1/workspaces/{id}", WORKSPACE_A).with(token(WORKSPACE_A)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(workspaceService);
    }
}
