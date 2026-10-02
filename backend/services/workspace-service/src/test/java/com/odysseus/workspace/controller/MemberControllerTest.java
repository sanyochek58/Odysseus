package com.odysseus.workspace.controller;

import com.odysseus.workspace.service.MemberService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;

@WebMvcTest(MemberController.class)
class MemberControllerTest extends ApiTestBase {

    @MockitoBean
    private MemberService memberService;

    private final UUID id = UUID.randomUUID();

    private MemberResponse member() {
        return new MemberResponse(id, "user-1", "user@acme.io", WorkspaceRole.MEMBER, Instant.now());
    }

    @Test
    @DisplayName("GET /members: список участников")
    void list_authenticated_returnsPage() throws Exception {
        when(memberService.list(any())).thenReturn(new PageImpl<>(List.of(member())));

        mvc.perform(get("/api/v1/members").with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("MEMBER"));
    }

    @Test
    @DisplayName("GET /members: без записи Member, 403 ProblemDetail")
    void list_noMember_returns403() throws Exception {
        mvc.perform(get("/api/v1/members").with(token(WORKSPACE_A)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("GET /members/{id}: без записи Member, 403 ProblemDetail")
    void get_noMember_returns403() throws Exception {
        mvc.perform(get("/api/v1/members/{id}", id).with(token(WORKSPACE_A)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("GET /members/{id}: участник другого workspace, 404")
    void get_notFound_returns404() throws Exception {
        when(memberService.get(id)).thenThrow(new NotFoundException("Участник не найден"));

        mvc.perform(get("/api/v1/members/{id}", id).with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /members/{id}/role: OWNER меняет роль")
    void changeRole_owner_returns200() throws Exception {
        when(memberService.changeRole(id, WorkspaceRole.ADMIN)).thenReturn(member());

        mvc.perform(put("/api/v1/members/{id}/role", id).with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /members/{id}/role: неизвестная роль, 400")
    void changeRole_unknownRole_returns400() throws Exception {
        mvc.perform(put("/api/v1/members/{id}/role", id).with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"GOD\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("PUT /members/{id}/role: ADMIN, 403")
    void changeRole_admin_returns403() throws Exception {
        mvc.perform(put("/api/v1/members/{id}/role", id).with(token(WORKSPACE_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("PUT /members/{id}/role: последний владелец, 409")
    void changeRole_lastOwner_returns409() throws Exception {
        when(memberService.changeRole(id, WorkspaceRole.MEMBER)).thenThrow(new ConflictException("последний владелец"));

        mvc.perform(put("/api/v1/members/{id}/role", id).with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /members/{id}: ADMIN удаляет, 204")
    void remove_admin_returns204() throws Exception {
        mvc.perform(delete("/api/v1/members/{id}", id).with(token(WORKSPACE_A, "ADMIN")))
                .andExpect(status().isNoContent());
        verify(memberService).remove(id);
    }

    @Test
    @DisplayName("DELETE /members/{id}: MEMBER, 403")
    void remove_member_returns403() throws Exception {
        mvc.perform(delete("/api/v1/members/{id}", id).with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(memberService);
    }

    @Test
    @DisplayName("DELETE /members/{id}: чужой участник, 404")
    void remove_notFound_returns404() throws Exception {
        doThrow(new NotFoundException("Участник не найден")).when(memberService).remove(id);

        mvc.perform(delete("/api/v1/members/{id}", id).with(token(WORKSPACE_A, "OWNER")))
                .andExpect(status().isNotFound());
    }
}
