package com.odysseus.events.outbox.store;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    /** Служебная таблица без бизнес-данных тенанта: условие по workspace_id не нужно (docs/guides/outbox.md). */
    @Query(value = "SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Outbox> lockUnsent(@Param("limit") int limit);
}
