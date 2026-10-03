package com.odysseus.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Базовый конверт события. Все контракты в {@code common-events} реализуют его record-ами
 * и меняются только добавлением полей.
 */
public interface DomainEvent {

    /** Уникальный идентификатор события, ключ идемпотентности консьюмеров. */
    UUID eventId();

    Instant occurredAt();

    /** Тенант события: консьюмер берёт workspace отсюда, не из заголовков. */
    UUID workspaceId();

    /** Версия контракта. */
    int version();
}
