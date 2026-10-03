package com.odysseus.events.outbox;

import com.odysseus.events.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** Планировщик outbox. Работает в системном контексте тенанта и трогает только таблицу outbox. */
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRelay outboxRelay;
    private final TenantScope tenantScope;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${odysseus.outbox.poll-interval-ms:1000}")
    public void publish() {
        try {
            tenantScope.runAsSystem(() -> {
                try {
                    outboxRelay.publishBatch(properties.batchSize());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Отправка outbox не удалась, повтор в следующем цикле: {}", e.getClass().getSimpleName());
        }
    }
}
