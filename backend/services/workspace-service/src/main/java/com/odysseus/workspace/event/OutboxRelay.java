package com.odysseus.workspace.event;

import com.odysseus.workspace.entity.Outbox;
import com.odysseus.workspace.repository.OutboxRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отправляет пачку неотправленных строк outbox в Kafka (at-least-once). Строки блокируются
 * {@code FOR UPDATE SKIP LOCKED}, поэтому параллельные реплики не дублируют отправку.
 * Вызывается {@link OutboxPublisher} в системном контексте; отдельный бин ради работы @Transactional.
 */
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    /** Возвращает число отправленных строк. Ошибка отправки откатывает пометки, строка уйдёт повторно. */
    @Transactional
    public int publishBatch(int batchSize) throws Exception {
        List<Outbox> batch = outboxRepository.lockUnsent(batchSize);
        for (Outbox row : batch) {
            kafkaTemplate.send(row.getTopic(), row.getAggregateId().toString(), row.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            row.setSentAt(clock.instant());
        }
        return batch.size();
    }
}
