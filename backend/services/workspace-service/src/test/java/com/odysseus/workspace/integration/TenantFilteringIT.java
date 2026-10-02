package com.odysseus.workspace.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.odysseus.workspace.config.TenantContext;
import com.odysseus.workspace.config.WorkspaceRole;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.repository.MemberRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** {@code @TenantId} на реальной БД: фильтр по тенанту и запрет чтения и записи без контекста. */
class TenantFilteringIT extends IntegrationTestBase {

    @Autowired
    private MemberRepository memberRepository;

    private Member save(UUID workspaceId, String userId) {
        return TenantContext.callAs(workspaceId, () -> memberRepository.save(
                Member.builder().userId(userId).email(userId + "@acme.io").role(WorkspaceRole.MEMBER).build()));
    }

    @Test
    @DisplayName("findAll: каждый тенант видит только свои строки, workspace_id проставлен из контекста")
    void findAll_twoTenants_returnsOnlyOwnRows() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Member memberA = save(a, "user-a");
        Member memberB = save(b, "user-b");

        List<Member> seenByA = TenantContext.callAs(a, () -> memberRepository.findAll());
        List<Member> seenByB = TenantContext.callAs(b, () -> memberRepository.findAll());

        assertThat(seenByA).extracting(Member::getId).containsExactly(memberA.getId());
        assertThat(seenByB).extracting(Member::getId).containsExactly(memberB.getId());
        assertThat(memberA.getWorkspaceId()).isEqualTo(a);
    }

    @Test
    @DisplayName("findById: чужая строка недоступна по id, удаление чужой строки ничего не удаляет")
    void findById_foreignRow_notVisibleAndNotDeletable() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Member memberB = save(b, "user-b");

        assertThat(TenantContext.callAs(a, () -> memberRepository.findById(memberB.getId()))).isEmpty();
        TenantContext.runAs(a, () -> memberRepository.deleteById(memberB.getId()));

        assertThat(TenantContext.callAs(b, () -> memberRepository.findById(memberB.getId()))).isPresent();
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM member WHERE id = ?", Integer.class, memberB.getId());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("чтение без контекста тенанта (NO_TENANT): запрещено")
    void read_withoutTenantContext_isForbidden() {
        UUID a = UUID.randomUUID();
        save(a, "user-a");

        assertThatThrownBy(() -> memberRepository.findAll()).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> memberRepository.count()).hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("запись без контекста тенанта: запрещена, строка не появилась")
    void write_withoutTenantContext_isForbidden() {
        String userId = "ghost-" + UUID.randomUUID();
        Member member = Member.builder().userId(userId).role(WorkspaceRole.MEMBER).build();

        assertThatThrownBy(() -> memberRepository.save(member)).hasRootCauseInstanceOf(IllegalStateException.class);

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM member WHERE user_id = ?", Integer.class, userId);
        assertThat(rows).isZero();
    }

    @Test
    @DisplayName("запись тенантной сущности в системном контексте: запрещена")
    void write_inSystemContext_isForbidden() {
        String userId = "system-" + UUID.randomUUID();
        Member member = Member.builder().userId(userId).role(WorkspaceRole.MEMBER).build();

        assertThatThrownBy(() -> TenantContext.runAsSystem(() -> memberRepository.save(member)))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM member WHERE user_id = ?", Integer.class, userId);
        assertThat(rows).isZero();
    }
}
