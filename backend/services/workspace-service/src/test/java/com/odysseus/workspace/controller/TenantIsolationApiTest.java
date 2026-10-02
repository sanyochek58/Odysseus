package com.odysseus.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.odysseus.workspace.config.TenantContext;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.WorkspaceResponse;
import com.odysseus.workspace.exception.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Изоляция тенантов на веб-уровне: тенант берётся только из токена. Изоляцию на уровне БД проверяют интеграционные тесты. */
class TenantIsolationApiTest extends ApiTestBase {

    @Test
    @DisplayName("GET /workspaces/{id}: пользователь A запрашивает workspace B, 404 и сервис работает в контексте A")
    void get_otherWorkspace_returns404InTenantA() throws Exception {
        List<UUID> seenTenants = new ArrayList<>();
        when(workspaceService.get(WORKSPACE_B)).thenAnswer(invocation -> {
            seenTenants.add(TenantContext.requireWorkspaceId());
            throw new NotFoundException("Workspace не найден");
        });

        mvc.perform(get("/api/v1/workspaces/{id}", WORKSPACE_B).with(token(WORKSPACE_A, "OWNER")))
                .andExpect(status().isNotFound());

        assertThat(seenTenants).containsExactly(WORKSPACE_A);
    }

    @Test
    @DisplayName("POST /workspaces: workspaceId из тела и заголовков игнорируется, тенант из токена")
    void create_foreignWorkspaceIdInBodyAndHeader_usesTokenTenant() throws Exception {
        List<UUID> seenTenants = new ArrayList<>();
        when(workspaceService.create(any(), any(), any())).thenAnswer(invocation -> {
            seenTenants.add(TenantContext.requireWorkspaceId());
            return new WorkspaceResponse(WORKSPACE_A, "Acme", Instant.now(), Instant.now());
        });

        mvc.perform(post("/api/v1/workspaces").with(token(WORKSPACE_A, "OWNER"))
                        .header("X-Workspace-Id", WORKSPACE_B.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"workspaceId\":\"" + WORKSPACE_B + "\"}"))
                .andExpect(status().isCreated());

        assertThat(seenTenants).containsExactly(WORKSPACE_A);
    }

    @Test
    @DisplayName("Роль: OWNER в A и MEMBER в B, с токеном B операция OWNER даёт 403")
    void changeRole_ownerInAMemberInB_tokenB_returns403() throws Exception {
        when(memberRolePort.findRole(WORKSPACE_A, USER_ID)).thenReturn(Optional.of(WorkspaceRole.OWNER));
        when(memberRolePort.findRole(WORKSPACE_B, USER_ID)).thenReturn(Optional.of(WorkspaceRole.MEMBER));

        mvc.perform(put("/api/v1/members/{id}/role", UUID.randomUUID()).with(tokenOf(WORKSPACE_B, true))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verify(memberRolePort).findRole(WORKSPACE_B, USER_ID);
        verify(memberRolePort, never()).findRole(eq(WORKSPACE_A), any());
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("Подписка проверяется по workspace из токена, а не из запроса")
    void write_guardUsesTokenTenant() throws Exception {
        List<UUID> checked = new ArrayList<>();
        when(subscriptionExpiryPort.findExpiresAt(any())).thenAnswer(invocation -> {
            checked.add(invocation.getArgument(0));
            return java.util.Optional.of(Instant.now().plusSeconds(60));
        });
        when(workspaceService.create(any(), any(), any()))
                .thenReturn(new WorkspaceResponse(WORKSPACE_A, "Acme", Instant.now(), Instant.now()));

        mvc.perform(post("/api/v1/workspaces").with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated());

        assertThat(checked).containsExactly(WORKSPACE_A);
    }
}
