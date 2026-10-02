package com.odysseus.workspace.event;

import com.odysseus.workspace.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Планировщик outbox. Работает в системном контексте тенанта и трогает только таблицу outbox. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRelay outboxRelay;

    @Value("${odysseus.outbox.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${odysseus.outbox.poll-interval-ms:1000}")
    public void publish() {
        try {
            TenantContext.runAsSystem(() -> {
                try {
                    outboxRelay.publishBatch(batchSize);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Отправка outbox не удалась, повтор в следующем цикле: {}", e.getClass().getSimpleName());
        }
    }
}
