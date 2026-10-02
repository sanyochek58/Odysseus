package com.odysseus.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.repository.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberRoleAdapterTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberRoleAdapter adapter;

    @Test
    @DisplayName("findRole: участник получает роль из Member")
    void findRole_member_returnsRole() {
        when(memberRepository.findRole(WORKSPACE_ID, "user-1")).thenReturn(Optional.of(WorkspaceRole.OWNER));

        assertThat(adapter.findRole(WORKSPACE_ID, "user-1")).contains(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("findRole: нет записи Member, пусто")
    void findRole_noMember_returnsEmpty() {
        when(memberRepository.findRole(WORKSPACE_ID, "user-1")).thenReturn(Optional.empty());

        assertThat(adapter.findRole(WORKSPACE_ID, "user-1")).isEmpty();
    }

    @Test
    @DisplayName("findRole: без sub или workspace, пусто без запроса в БД")
    void findRole_missingIdentity_returnsEmptyWithoutQuery() {
        assertThat(adapter.findRole(WORKSPACE_ID, null)).isEmpty();
        assertThat(adapter.findRole(WORKSPACE_ID, " ")).isEmpty();
        assertThat(adapter.findRole(null, "user-1")).isEmpty();
        verifyNoInteractions(memberRepository);
    }
}
