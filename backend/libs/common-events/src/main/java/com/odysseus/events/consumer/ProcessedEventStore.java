package com.odysseus.events.consumer;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Доступ к таблице {@code processed_events} (служебная, без данных тенанта). */
@RequiredArgsConstructor
public class ProcessedEventStore {

    private final JdbcClient jdbcClient;

    /** Записывает пару (событие, консьюмер). {@code false}, если уже была записана: дубликат. */
    public boolean markProcessed(UUID eventId, String consumer) {
        return jdbcClient.sql("INSERT INTO processed_events (event_id, consumer) VALUES (:eventId, :consumer) "
                        + "ON CONFLICT (event_id, consumer) DO NOTHING")
                .param("eventId", eventId)
                .param("consumer", consumer)
                .update() > 0;
    }
}
