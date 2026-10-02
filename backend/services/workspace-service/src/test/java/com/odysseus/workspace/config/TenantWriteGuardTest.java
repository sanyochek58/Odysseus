package com.odysseus.workspace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpsertEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantWriteGuardTest {

    private final TenantWriteGuard guard = new TenantWriteGuard();

    /** Тенантная сущность. */
    static class TenantEntity {
        @TenantId
        UUID workspaceId;
    }

    /** Нетенантная сущность, как outbox. */
    static class PlainEntity {
        UUID id;
    }

    private static SharedSessionContractImplementor session(UUID tenant) {
        SharedSessionContractImplementor session = mock(SharedSessionContractImplementor.class);
        when(session.getTenantIdentifierValue()).thenReturn(tenant);
        return session;
    }

    private static PreInsertEvent insert(Object entity, UUID sessionTenant) {
        return new PreInsertEvent(entity, null, new Object[0], null, session(sessionTenant));
    }

    @Test
    @DisplayName("onPreInsert: тенант сессии совпадает с контекстом workspace, запись разрешена")
    void onPreInsert_matchingWorkspace_allows() {
        UUID workspaceId = UUID.randomUUID();
        PreInsertEvent event = insert(new TenantEntity(), workspaceId);

        boolean veto = TenantContext.callAs(workspaceId, () -> guard.onPreInsert(event));

        assertThat(veto).isFalse();
    }

    @Test
    @DisplayName("onPreInsert: без контекста запись запрещена")
    void onPreInsert_noContext_throws() {
        PreInsertEvent event = insert(new TenantEntity(), TenantContext.NO_TENANT);

        assertThatThrownBy(() -> guard.onPreInsert(event)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("onPreInsert: сессия открыта без контекста, запись в контексте workspace запрещена")
    void onPreInsert_sessionWithoutContext_throws() {
        PreInsertEvent event = insert(new TenantEntity(), TenantContext.NO_TENANT);

        assertThatThrownBy(() -> TenantContext.callAs(UUID.randomUUID(), () -> guard.onPreInsert(event)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("onPreInsert: тенант сессии от другого workspace, запись запрещена")
    void onPreInsert_otherWorkspaceSession_throws() {
        PreInsertEvent event = insert(new TenantEntity(), UUID.randomUUID());

        assertThatThrownBy(() -> TenantContext.callAs(UUID.randomUUID(), () -> guard.onPreInsert(event)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("onPreInsert: тенантная сущность в системном контексте запрещена")
    void onPreInsert_tenantEntityInSystemContext_throws() {
        PreInsertEvent event = insert(new TenantEntity(), TenantContext.SYSTEM);

        assertThatThrownBy(() -> TenantContext.callAsSystem(() -> guard.onPreInsert(event)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("onPreUpdate: нетенантная сущность в системном контексте разрешена")
    void onPreUpdate_plainEntityInSystemContext_allows() {
        PreUpdateEvent event = new PreUpdateEvent(
                new PlainEntity(), null, new Object[0], new Object[0], null, session(TenantContext.SYSTEM));

        boolean veto = TenantContext.callAsSystem(() -> guard.onPreUpdate(event));

        assertThat(veto).isFalse();
    }

    @Test
    @DisplayName("onPreDelete: без контекста удаление запрещено")
    void onPreDelete_noContext_throws() {
        PreDeleteEvent event = new PreDeleteEvent(
                new TenantEntity(), null, new Object[0], null, session(TenantContext.NO_TENANT));

        assertThatThrownBy(() -> guard.onPreDelete(event)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("onPreUpsert: без контекста upsert запрещён")
    void onPreUpsert_noContext_throws() {
        PreUpsertEvent event = new PreUpsertEvent(
                new TenantEntity(), null, new Object[0], null, session(TenantContext.NO_TENANT));

        assertThatThrownBy(() -> guard.onPreUpsert(event)).isInstanceOf(IllegalStateException.class);
    }
}
