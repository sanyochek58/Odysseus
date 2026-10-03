package com.odysseus.events.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.odysseus.events.TenantScope;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxPublisherTest {

    private final OutboxRelay relay = mock(OutboxRelay.class);
    private final OutboxProperties props = new OutboxProperties(true, 50, 1000,
            new OutboxProperties.Retry(3, 1000, 2.0, 30000));

    @Test
    @DisplayName("publish: отправка идёт в системном контексте с размером пачки из настроек")
    void publish_runsInSystemScope() throws Exception {
        AtomicBoolean system = new AtomicBoolean();
        TenantScope scope = new TenantScope() {
            @Override
            public void runAsSystem(Runnable action) {
                system.set(true);
                action.run();
            }

            @Override
            public void runAs(UUID workspaceId, Runnable action) {
                throw new AssertionError("не ожидается");
            }
        };

        new OutboxPublisher(relay, scope, props).publish();

        verify(relay).publishBatch(50);
        org.assertj.core.api.Assertions.assertThat(system).isTrue();
    }

    @Test
    @DisplayName("publish: ошибка отправки не выходит наружу, планировщик не останавливается")
    void publish_relayFails_swallowed() throws Exception {
        when(relay.publishBatch(50)).thenThrow(new RuntimeException("kafka down"));

        assertThatCode(() -> new OutboxPublisher(relay, TenantScope.NONE, props).publish()).doesNotThrowAnyException();
    }
}
