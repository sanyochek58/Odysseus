package com.odysseus.events.outbox;

import com.odysseus.events.DomainEvent;
import com.odysseus.events.outbox.store.Outbox;
import com.odysseus.events.outbox.store.OutboxRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Запись события в outbox внутри транзакции бизнес-операции. Отправку делает {@link OutboxPublisher}. */
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    /** Требует активной транзакции: событие и бизнес-изменение фиксируются атомарно. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, UUID aggregateId, UUID workspaceId, UUID eventId, Object event) {
        outboxRepository.save(Outbox.builder()
                .id(eventId)
                .topic(topic)
                .aggregateId(aggregateId)
                .workspaceId(workspaceId)
                .payload(jsonMapper.writeValueAsString(event))
                .build());
    }

    /** Короткая форма: идентификаторы берутся из конверта события. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, UUID aggregateId, DomainEvent event) {
        save(topic, aggregateId, event.workspaceId(), event.eventId(), event);
    }
}
