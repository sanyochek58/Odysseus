package com.odysseus.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odysseus.workspace.config.TenantContext;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.WorkspaceRequest;
import com.odysseus.workspace.dto.WorkspaceResponse;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.entity.Workspace;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.WorkspaceMapperImpl;
import com.odysseus.workspace.repository.MemberRepository;
import com.odysseus.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID otherWorkspaceId = UUID.randomUUID();

    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SubscriptionService subscriptionService;

    private WorkspaceService service() {
        return new WorkspaceService(workspaceRepository, memberRepository, subscriptionService, new WorkspaceMapperImpl());
    }

    @Test
    @DisplayName("create: новый workspace получает id тенанта, владельца и пробную подписку")
    void create_newWorkspace_createsOwnerAndTrial() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> i.getArgument(0));

        WorkspaceResponse response = TenantContext.callAs(workspaceId,
                () -> service().create(new WorkspaceRequest("  Acme "), "user-1", "Boss@Acme.io"));

        assertThat(response.id()).isEqualTo(workspaceId);
        assertThat(response.name()).isEqualTo("Acme");
        ArgumentCaptor<Member> member = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(member.capture());
        assertThat(member.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(member.getValue().getEmail()).isEqualTo("boss@acme.io");
        verify(subscriptionService).createTrial();
    }

    @Test
    @DisplayName("create: workspace уже есть, конфликт")
    void create_alreadyExists_throwsConflict() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);

        assertThatThrownBy(() -> TenantContext.runAs(workspaceId,
                () -> service().create(new WorkspaceRequest("Acme"), "u", null)))
                .isInstanceOf(ConflictException.class);
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: без контекста тенанта ошибка, workspaceId не берётся из других источников")
    void create_noTenantContext_throws() {
        assertThatThrownBy(() -> service().create(new WorkspaceRequest("Acme"), "u", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("get: чужой workspace неотличим от несуществующего, 404")
    void get_otherTenantWorkspace_throwsNotFound() {
        // репозиторий с @TenantId не видит чужую строку
        when(workspaceRepository.findById(otherWorkspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> TenantContext.runAs(workspaceId, () -> service().get(otherWorkspaceId)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("rename: меняет имя своего workspace")
    void rename_ownWorkspace_updatesName() {
        Workspace workspace = Workspace.builder().id(workspaceId).name("Old").build();
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(workspaceRepository.save(workspace)).thenReturn(workspace);

        WorkspaceResponse response = service().rename(workspaceId, new WorkspaceRequest(" New "));

        assertThat(response.name()).isEqualTo("New");
    }

    @Test
    @DisplayName("rename: чужой workspace, 404 и ничего не сохраняется")
    void rename_otherTenantWorkspace_throwsNotFound() {
        when(workspaceRepository.findById(otherWorkspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().rename(otherWorkspaceId, new WorkspaceRequest("X")))
                .isInstanceOf(NotFoundException.class);
        verify(workspaceRepository, never()).save(any());
    }
}
