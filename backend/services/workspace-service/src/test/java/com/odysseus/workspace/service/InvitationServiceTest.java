package com.odysseus.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.InvitationRequest;
import com.odysseus.workspace.dto.InvitationResponse;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.entity.Invitation;
import com.odysseus.workspace.entity.InvitationStatus;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.ForbiddenOperationException;
import com.odysseus.workspace.exception.InvalidRequestException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.InvitationMapperImpl;
import com.odysseus.workspace.mapper.MemberMapperImpl;
import com.odysseus.workspace.repository.InvitationRepository;
import com.odysseus.workspace.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private final UUID id = UUID.randomUUID();

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private MemberRepository memberRepository;

    private InvitationService service() {
        return new InvitationService(invitationRepository, memberRepository, new InvitationMapperImpl(),
                new MemberMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Invitation pending() {
        return Invitation.builder().id(id).email("new@acme.io").role(WorkspaceRole.MEMBER)
                .status(InvitationStatus.PENDING).invitedBy("admin").expiresAt(NOW.plusSeconds(3600)).build();
    }

    @Test
    @DisplayName("create: сохраняет приглашение с нормализованным email и сроком")
    void create_valid_savesPending() {
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));

        InvitationResponse response = service().create(new InvitationRequest(" New@Acme.io ", WorkspaceRole.MEMBER), "admin");

        assertThat(response.email()).isEqualTo("new@acme.io");
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(InvitationService.TTL));
    }

    @Test
    @DisplayName("create: роль OWNER, 400")
    void create_ownerRole_throwsInvalid() {
        assertThatThrownBy(() -> service().create(new InvitationRequest("a@b.io", WorkspaceRole.OWNER), "admin"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("create: email уже участник, 409")
    void create_existingMember_throwsConflict() {
        when(memberRepository.existsByEmailIgnoreCase("a@b.io")).thenReturn(true);

        assertThatThrownBy(() -> service().create(new InvitationRequest("a@b.io", WorkspaceRole.MEMBER), "admin"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("create: повторное приглашение на тот же email, 409")
    void create_duplicatePending_throwsConflict() {
        when(invitationRepository.existsByEmailAndStatus("a@b.io", InvitationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service().create(new InvitationRequest("a@b.io", WorkspaceRole.MEMBER), "admin"))
                .isInstanceOf(ConflictException.class);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("list: возвращает страницу")
    void list_returnsPage() {
        PageRequest pageable = PageRequest.of(0, 5);
        when(invitationRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(pending())));

        assertThat(service().list(pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("revoke: помечает приглашение отозванным")
    void revoke_pending_setsRevoked() {
        Invitation invitation = pending();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(invitation)).thenReturn(invitation);

        assertThat(service().revoke(id).status()).isEqualTo(InvitationStatus.REVOKED);
    }

    @Test
    @DisplayName("revoke: чужое приглашение, 404")
    void revoke_otherTenant_throwsNotFound() {
        when(invitationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().revoke(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("revoke: повторный отзыв, 409")
    void revoke_alreadyRevoked_throwsConflict() {
        Invitation invitation = pending();
        invitation.setStatus(InvitationStatus.REVOKED);
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().revoke(id)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("accept: создаёт участника с ролью из приглашения")
    void accept_matchingEmail_createsMember() {
        Invitation invitation = pending();
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));
        when(memberRepository.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));

        MemberResponse member = service().accept(id, "user-2", "NEW@acme.io");

        assertThat(member.role()).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("accept: чужой email, 403")
    void accept_otherEmail_throwsForbidden() {
        when(invitationRepository.findById(id)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service().accept(id, "user-2", "evil@x.io"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("accept: токен без email, 403")
    void accept_noEmail_throwsForbidden() {
        when(invitationRepository.findById(id)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service().accept(id, "user-2", null)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("accept: срок истёк, 409")
    void accept_expired_throwsConflict() {
        Invitation invitation = pending();
        invitation.setExpiresAt(NOW);
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().accept(id, "user-2", "new@acme.io")).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("accept: пользователь уже участник, 409")
    void accept_alreadyMember_throwsConflict() {
        when(invitationRepository.findById(id)).thenReturn(Optional.of(pending()));
        when(memberRepository.existsByUserId("user-2")).thenReturn(true);

        assertThatThrownBy(() -> service().accept(id, "user-2", "new@acme.io")).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("accept: повторное принятие, 409")
    void accept_alreadyAccepted_throwsConflict() {
        Invitation invitation = pending();
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().accept(id, "user-2", "new@acme.io")).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("accept: чужой email и уже обработанное приглашение, 403 (статус не раскрывается)")
    void accept_otherEmailAndAccepted_throwsForbidden() {
        Invitation invitation = pending();
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().accept(id, "user-2", "evil@x.io"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("accept: чужой email и отозванное приглашение, 403")
    void accept_otherEmailAndRevoked_throwsForbidden() {
        Invitation invitation = pending();
        invitation.setStatus(InvitationStatus.REVOKED);
        when(invitationRepository.findById(id)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service().accept(id, "user-2", null))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
