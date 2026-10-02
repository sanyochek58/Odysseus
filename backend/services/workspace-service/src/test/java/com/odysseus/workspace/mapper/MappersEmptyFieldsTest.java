package com.odysseus.workspace.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.odysseus.workspace.dto.MemberResponse;
import com.odysseus.workspace.entity.Member;
import com.odysseus.workspace.entity.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MappersEmptyFieldsTest {

    @Test
    @DisplayName("toResponse: null и пустые поля не ломают маппинг")
    void toResponse_nullAndEmptyFields_mapsWithoutErrors() {
        assertThat(new WorkspaceMapperImpl().toResponse(null)).isNull();
        assertThat(new InvitationMapperImpl().toResponse(null)).isNull();
        assertThat(new SubscriptionMapperImpl().toResponse(null)).isNull();

        MemberResponse member = new MemberMapperImpl().toResponse(Member.builder().build());
        assertThat(member).isNotNull();
        assertThat(member.id()).isNull();
        assertThat(member.email()).isNull();
        assertThat(member.role()).isNull();

        assertThat(new WorkspaceMapperImpl().toResponse(Workspace.builder().build()).name()).isNull();
    }
}
