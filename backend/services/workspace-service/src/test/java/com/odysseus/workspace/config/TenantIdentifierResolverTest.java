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
    @DisplayName("resolveCurrentTenantIdentifier: без контекста отдаёт служебный id без данных, не корневой")
    void resolveCurrentTenantIdentifier_noContext_returnsNoTenant() {
        UUID resolved = resolver.resolveCurrentTenantIdentifier();

        assertThat(resolved).isEqualTo(TenantContext.NO_TENANT);
        assertThat(resolver.isRoot(resolved)).isFalse();
    }

    @Test
    @DisplayName("callAs: служебный id отсутствия контекста нельзя выдать за workspace")
    void callAs_noTenantId_throws() {
        assertThatThrownBy(() -> TenantContext.callAs(TenantContext.NO_TENANT, () -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isRoot: ни workspace, ни системный контекст не корневые")
    void isRoot_anyTenant_returnsFalse() {
        assertThat(resolver.isRoot(TenantContext.SYSTEM)).isFalse();
        assertThat(resolver.isRoot(TenantContext.NO_TENANT)).isFalse();
        assertThat(resolver.isRoot(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("validateExistingCurrentSessions: проверка сессий включена")
    void validateExistingCurrentSessions_always_returnsTrue() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }
}
