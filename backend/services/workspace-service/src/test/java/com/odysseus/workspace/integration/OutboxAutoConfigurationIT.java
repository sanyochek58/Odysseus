package com.odysseus.workspace.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.odysseus.events.TenantScope;
import com.odysseus.events.outbox.OutboxPublisher;
import com.odysseus.events.outbox.OutboxRelay;
import com.odysseus.events.outbox.OutboxService;
import com.odysseus.workspace.config.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Автоконфигурация outbox из common-events и работа публикатора в системном контексте. */
class OutboxAutoConfigurationIT extends IntegrationTestBase {

    @Autowired
    private OutboxService outboxService;
    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private OutboxPublisher outboxPublisher;
    @Autowired
    private TenantScope tenantScope;

    @Test
    @DisplayName("автоконфигурация: бины outbox из библиотеки подхвачены, TenantScope из сервиса")
    void context_outboxBeansFromLibrary_present() {
        assertThat(outboxService).isNotNull();
        assertThat(outboxRelay).isNotNull();
        assertThat(outboxPublisher).isNotNull();
        assertThat(tenantScope.getClass().getName()).startsWith("com.odysseus.workspace.config.");
    }

    @Test
    @DisplayName("публикатор: работает в системном контексте и не оставляет тенант после себя")
    void publish_runsAsSystem_doesNotLeakTenant() {
        // без контекста: после вызова контекст остаётся пустым
        outboxPublisher.publish();
        assertThat(TenantContext.isSystem()).isFalse();
        assertThat(TenantContext.currentWorkspaceId()).isEmpty();

        // внутри тенанта A: после вызова снова тенант A, а не системный
        UUID workspaceA = UUID.randomUUID();
        TenantContext.runAs(workspaceA, () -> {
            outboxPublisher.publish();
            assertThat(TenantContext.isSystem()).isFalse();
            assertThat(TenantContext.currentWorkspaceId()).contains(workspaceA);
        });
        assertThat(TenantContext.currentWorkspaceId()).isEmpty();
    }
}
