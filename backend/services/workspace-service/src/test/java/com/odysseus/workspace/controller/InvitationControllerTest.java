package com.odysseus.workspace.controller;

import com.odysseus.workspace.service.InvitationService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.InvitationResponse;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.entity.InvitationStatus;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.ForbiddenOperationException;
import com.odysseus.workspace.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;

@WebMvcTest(InvitationController.class)
class InvitationControllerTest extends ApiTestBase {

    @MockitoBean
    private InvitationService invitationService;

    private static final String BODY = "{\"email\":\"new@acme.io\",\"role\":\"MEMBER\"}";

    private final UUID id = UUID.randomUUID();

    private InvitationResponse invitation() {
        return new InvitationResponse(id, "new@acme.io", WorkspaceRole.MEMBER, InvitationStatus.PENDING, "user-1",
                Instant.now().plusSeconds(3600), Instant.now());
    }

    @Test
    @DisplayName("POST /invitations: ADMIN создаёт приглашение, 201")
    void create_admin_returns201() throws Exception {
        when(invitationService.create(any(), eq("user-1"))).thenReturn(invitation());

        mvc.perform(post("/api/v1/invitations").with(token(WORKSPACE_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /invitations: невалидный email, 400")
    void create_invalidEmail_returns400() throws Exception {
        mvc.perform(post("/api/v1/invitations").with(token(WORKSPACE_A, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invitationService);
    }

    @Test
    @DisplayName("POST /invitations: MEMBER, 403")
    void create_member_returns403() throws Exception {
        mvc.perform(post("/api/v1/invitations").with(token(WORKSPACE_A, "MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(invitationService);
    }

    @Test
    @DisplayName("POST /invitations: дубликат, 409")
    void create_duplicate_returns409() throws Exception {
        when(invitationService.create(any(), any())).thenThrow(new ConflictException("уже отправлено"));

        mvc.perform(post("/api/v1/invitations").with(token(WORKSPACE_A, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /invitations: список")
    void list_admin_returnsPage() throws Exception {
        when(invitationService.list(any())).thenReturn(new PageImpl<>(List.of(invitation())));

        mvc.perform(get("/api/v1/invitations").with(token(WORKSPACE_A, "OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("new@acme.io"));
    }

    @Test
    @DisplayName("GET /invitations: без записи Member, 403 ProblemDetail")
    void list_noMember_returns403() throws Exception {
        mvc.perform(get("/api/v1/invitations").with(token(WORKSPACE_A)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(invitationService);
    }

    @Test
    @DisplayName("DELETE /invitations/{id}: чужое приглашение, 404")
    void revoke_notFound_returns404() throws Exception {
        when(invitationService.revoke(id)).thenThrow(new NotFoundException("Приглашение не найдено"));

        mvc.perform(delete("/api/v1/invitations/{id}", id).with(token(WORKSPACE_A, "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /invitations/{id}: отзыв, 200")
    void revoke_admin_returns200() throws Exception {
        when(invitationService.revoke(id)).thenReturn(invitation());

        mvc.perform(delete("/api/v1/invitations/{id}", id).with(token(WORKSPACE_A, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /invitations/{id}/accept: принятие, 200")
    void accept_matching_returns200() throws Exception {
        when(invitationService.accept(id, "user-1", "user@acme.io"))
                .thenReturn(new MemberResponse(UUID.randomUUID(), "user-1", "user@acme.io", WorkspaceRole.MEMBER, Instant.now()));

        mvc.perform(post("/api/v1/invitations/{id}/accept", id).with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /invitations/{id}/accept: email не подтверждён, 403 ProblemDetail, сервис не вызывается")
    void accept_emailNotVerified_returns403() throws Exception {
        mvc.perform(post("/api/v1/invitations/{id}/accept", id).with(tokenOf(WORKSPACE_A, false)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(invitationService);
    }

    @Test
    @DisplayName("POST /invitations/{id}/accept: без claim email_verified, 403")
    void accept_emailVerifiedMissing_returns403() throws Exception {
        mvc.perform(post("/api/v1/invitations/{id}/accept", id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(j -> j.subject("user-1")
                                        .claim("email", "user@acme.io")
                                        .claim("organization", java.util.Map.of("acme",
                                                java.util.Map.of("id", WORKSPACE_A.toString()))))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(invitationService);
    }

    @Test
    @DisplayName("POST /invitations/{id}/accept: пользователь без записи Member с подтверждённым email принимает, 200")
    void accept_noMemberVerifiedEmail_returns200() throws Exception {
        when(invitationService.accept(id, "user-1", "user@acme.io"))
                .thenReturn(new MemberResponse(UUID.randomUUID(), "user-1", "user@acme.io", WorkspaceRole.MEMBER, Instant.now()));

        mvc.perform(post("/api/v1/invitations/{id}/accept", id).with(token(WORKSPACE_A)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /invitations/{id}/accept: чужой email, 403")
    void accept_otherEmail_returns403() throws Exception {
        when(invitationService.accept(any(), any(), any())).thenThrow(new ForbiddenOperationException("другой email"));

        mvc.perform(post("/api/v1/invitations/{id}/accept", id).with(token(WORKSPACE_A, "MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
