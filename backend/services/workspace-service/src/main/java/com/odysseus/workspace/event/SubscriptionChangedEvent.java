package com.odysseus.workspace.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Контракт события {@code workspace.subscription.changed} (ADR-001). Менять только добавлением полей.
 * Публикуется через outbox при создании workspace и продлении подписки.
 */
public record SubscriptionChangedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID workspaceId,
        int version,
        Instant expiresAt) {

    public static final String TOPIC = "workspace.subscription.changed";
    public static final int CURRENT_VERSION = 1;
}
