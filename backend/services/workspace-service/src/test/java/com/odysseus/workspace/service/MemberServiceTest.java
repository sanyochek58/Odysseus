package com.odysseus.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.exception.ConflictException;
import com.odysseus.workspace.exception.NotFoundException;
import com.odysseus.workspace.mapper.MemberMapperImpl;
import com.odysseus.workspace.repository.MemberRepository;
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
class MemberServiceTest {

    private final UUID id = UUID.randomUUID();

    @Mock
    private MemberRepository memberRepository;

    private MemberService service() {
        return new MemberService(memberRepository, new MemberMapperImpl());
    }

    private Member member(WorkspaceRole role) {
        return Member.builder().id(id).userId("u").role(role).build();
    }

    @Test
    @DisplayName("list: возвращает страницу участников")
    void list_returnsPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(memberRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(member(WorkspaceRole.MEMBER))));

        assertThat(service().list(pageable).getContent()).hasSize(1);
    }

    @Test
    @DisplayName("get: участник другого workspace, 404")
    void get_notVisible_throwsNotFound() {
        when(memberRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("get: возвращает участника")
    void get_existing_returnsMember() {
        when(memberRepository.findById(id)).thenReturn(Optional.of(member(WorkspaceRole.ADMIN)));

        assertThat(service().get(id).role()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @DisplayName("changeRole: обычная смена роли")
    void changeRole_member_updatesRole() {
        Member m = member(WorkspaceRole.MEMBER);
        when(memberRepository.findById(id)).thenReturn(Optional.of(m));
        when(memberRepository.save(m)).thenReturn(m);

        MemberResponse response = service().changeRole(id, WorkspaceRole.MANAGER);

        assertThat(response.role()).isEqualTo(WorkspaceRole.MANAGER);
    }

    @Test
    @DisplayName("changeRole: понижение последнего OWNER, 409")
    void changeRole_lastOwner_throwsConflict() {
        when(memberRepository.findById(id)).thenReturn(Optional.of(member(WorkspaceRole.OWNER)));
        when(memberRepository.findAllByRole(WorkspaceRole.OWNER))
                .thenReturn(List.of(member(WorkspaceRole.OWNER)));

        assertThatThrownBy(() -> service().changeRole(id, WorkspaceRole.ADMIN)).isInstanceOf(ConflictException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeRole: OWNER при нескольких владельцах можно понизить")
    void changeRole_ownerWithOthers_updatesRole() {
        Member m = member(WorkspaceRole.OWNER);
        when(memberRepository.findById(id)).thenReturn(Optional.of(m));
        when(memberRepository.findAllByRole(WorkspaceRole.OWNER))
                .thenReturn(List.of(member(WorkspaceRole.OWNER), member(WorkspaceRole.OWNER)));
        when(memberRepository.save(m)).thenReturn(m);

        assertThat(service().changeRole(id, WorkspaceRole.ADMIN).role()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @DisplayName("remove: удаляет участника")
    void remove_member_deletes() {
        Member m = member(WorkspaceRole.MEMBER);
        when(memberRepository.findById(id)).thenReturn(Optional.of(m));

        service().remove(id);

        verify(memberRepository).delete(m);
    }

    @Test
    @DisplayName("remove: последний OWNER, 409")
    void remove_lastOwner_throwsConflict() {
        when(memberRepository.findById(id)).thenReturn(Optional.of(member(WorkspaceRole.OWNER)));
        when(memberRepository.findAllByRole(WorkspaceRole.OWNER))
                .thenReturn(List.of(member(WorkspaceRole.OWNER)));

        assertThatThrownBy(() -> service().remove(id)).isInstanceOf(ConflictException.class);
        verify(memberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("remove: чужой участник, 404 и удаления нет")
    void remove_otherTenantMember_throwsNotFound() {
        when(memberRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().remove(id)).isInstanceOf(NotFoundException.class);
        verify(memberRepository, never()).delete(any());
    }
}
