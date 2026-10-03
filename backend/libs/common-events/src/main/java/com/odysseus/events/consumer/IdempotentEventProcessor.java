package com.odysseus.events.consumer;

import com.odysseus.events.DomainEvent;
import com.odysseus.events.TenantScope;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Идемпотентная обработка события. Тенант выставляется из {@code event.workspaceId()} до открытия
 * транзакции, затем в одной транзакции фиксируется {@code (eventId, consumer)} и выполняется обработчик.
 * Дубликат пропускается, ошибка обработчика откатывает и отметку, и изменения (retry, затем {@code .dlt}).
 * Вызывать из отдельного бина-слушателя, не через {@code this}.
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotentEventProcessor {

    private final ProcessedEventStore store;
    private final TransactionOperations transactions;
    private final TenantScope tenantScope;

    /** Возвращает {@code true}, если событие обработано сейчас, {@code false} если это дубликат. */
    public boolean process(String consumer, DomainEvent event, Runnable handler) {
        AtomicBoolean handled = new AtomicBoolean();
        tenantScope.runAs(event.workspaceId(), () -> transactions.executeWithoutResult(status -> {
            if (!store.markProcessed(event.eventId(), consumer)) {
                log.debug("Дубликат события пропущен: consumer={}, eventId={}", consumer, event.eventId());
                return;
            }
            handler.run();
            handled.set(true);
        }));
        return handled.get();
    }
}
