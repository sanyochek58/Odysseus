package com.odysseus.workspace.service;

import com.odysseus.workspace.entity.Outbox;
import com.odysseus.workspace.repository.OutboxRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Запись события в outbox внутри транзакции бизнес-операции. Отправку делает {@code OutboxPublisher}. */
@Service
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
}
