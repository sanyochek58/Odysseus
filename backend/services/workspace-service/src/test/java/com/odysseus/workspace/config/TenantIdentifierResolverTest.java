package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantIdentifierResolverTest {

    private final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

    @Test
    @DisplayName("resolveCurrentTenantIdentifier: в контексте workspace отдаёт его id")
    void resolveCurrentTenantIdentifier_workspaceContext_returnsWorkspaceId() {
        UUID workspaceId = UUID.randomUUID();

        UUID resolved = TenantContext.callAs(workspaceId, resolver::resolveCurrentTenantIdentifier);

        assertThat(resolved).isEqualTo(workspaceId);
    }

    @Test
    @DisplayName("resolveCurrentTenantIdentifier: в системном контексте отдаёт служебный id")
    void resolveCurrentTenantIdentifier_systemContext_returnsSystemId() {
        UUID resolved = TenantContext.callAsSystem(resolver::resolveCurrentTenantIdentifier);

        assertThat(resolved).isEqualTo(TenantContext.SYSTEM);
    }

    @Test
    @DisplayName("resolveCurrentTenantIdentifier: без контекста исключение")
    void resolveCurrentTenantIdentifier_noContext_throws() {
        assertThatThrownBy(resolver::resolveCurrentTenantIdentifier).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isRoot: ни workspace, ни системный контекст не корневые")
    void isRoot_anyTenant_returnsFalse() {
        assertThat(resolver.isRoot(TenantContext.SYSTEM)).isFalse();
        assertThat(resolver.isRoot(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("validateExistingCurrentSessions: проверка сессий включена")
    void validateExistingCurrentSessions_always_returnsTrue() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }
}
