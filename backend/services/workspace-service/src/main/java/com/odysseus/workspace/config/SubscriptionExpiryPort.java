package com.odysseus.workspace.config;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт к локальной проекции подписки (ADR-001). Реализует backend-dev поверх сущности Subscription.
 * Вызывается внутри {@link TenantContext} запроса.
 */
public interface SubscriptionExpiryPort {

    /** Срок подписки workspace. Пусто, если подписки нет: запись тогда запрещена. */
    Optional<Instant> findExpiresAt(UUID workspaceId);
}
