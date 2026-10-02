package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantStatementGuardTest {

    private static final String SQL = "select w.id from workspace w where w.workspace_id = ?";

    private final TenantStatementGuard guard = new TenantStatementGuard();

    @Test
    @DisplayName("inspect: в контексте workspace SQL пропускается без изменений")
    void inspect_workspaceContext_returnsSql() {
        String result = TenantContext.callAs(UUID.randomUUID(), () -> guard.inspect(SQL));

        assertThat(result).isEqualTo(SQL);
    }

    @Test
    @DisplayName("inspect: в системном контексте SQL пропускается")
    void inspect_systemContext_returnsSql() {
        String result = TenantContext.callAsSystem(() -> guard.inspect(SQL));

        assertThat(result).isEqualTo(SQL);
    }

    @Test
    @DisplayName("inspect: без контекста SQL запрещён")
    void inspect_noContext_throws() {
        assertThatThrownBy(() -> guard.inspect(SQL)).isInstanceOf(IllegalStateException.class);
    }
}
