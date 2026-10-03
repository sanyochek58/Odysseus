package com.odysseus.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.odysseus.events.SubscriptionChangedEvent;
import com.odysseus.events.TenantScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class IdempotentEventProcessorTest {

    private final ProcessedEventStore store = mock(ProcessedEventStore.class);
    private final List<UUID> tenants = new ArrayList<>();
    private final TenantScope scope = new TenantScope() {
        @Override
        public void runAsSystem(Runnable action) {
            action.run();
        }

        @Override
        public void runAs(UUID workspaceId, Runnable action) {
            tenants.add(workspaceId);
            action.run();
        }
    };
    private final IdempotentEventProcessor processor =
            new IdempotentEventProcessor(store, TransactionOperations.withoutTransaction(), scope);

    private final UUID workspaceId = UUID.randomUUID();
    private final SubscriptionChangedEvent event =
            new SubscriptionChangedEvent(UUID.randomUUID(), Instant.now(), workspaceId, 1, Instant.now());

    @Test
    @DisplayName("process: новое событие обрабатывается в тенанте из события")
    void process_newEvent_handledInEventTenant() {
        when(store.markProcessed(event.eventId(), "c")).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();

        boolean handled = processor.process("c", event, calls::incrementAndGet);

        assertThat(handled).isTrue();
        assertThat(calls).hasValue(1);
        assertThat(tenants).containsExactly(workspaceId);
    }

    @Test
    @DisplayName("process: дубликат пропускается без вызова обработчика")
    void process_duplicate_skipped() {
        when(store.markProcessed(event.eventId(), "c")).thenReturn(false);
        AtomicInteger calls = new AtomicInteger();

        boolean handled = processor.process("c", event, calls::incrementAndGet);

        assertThat(handled).isFalse();
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("process: ошибка обработчика пробрасывается для retry и dlt")
    void process_handlerFails_propagates() {
        when(store.markProcessed(event.eventId(), "c")).thenReturn(true);

        assertThatThrownBy(() -> processor.process("c", event, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
    }
}
