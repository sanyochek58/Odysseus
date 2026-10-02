package com.odysseus.workspace.config;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Текущий тенант (workspace) потока.
 * HTTP: заполняет {@link TenantContextFilter} из проверенного JWT.
 * Kafka: {@code runAs(event.workspaceId(), ...)}. Планировщик outbox: {@code runAsSystem(...)}, только таблица outbox.
 * Контексты не вкладываются, кроме повтора того же самого: смена тенанта внутри активного контекста запрещена.
 */
public final class TenantContext {

    /** Служебный идентификатор системного контекста. Не принадлежит ни одному workspace, бизнес-данных под ним нет. */
    public static final UUID SYSTEM = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Выполняет действие от имени workspace. */
    public static void runAs(UUID workspaceId, Runnable action) {
        Objects.requireNonNull(action, "action");
        callAs(workspaceId, () -> {
            action.run();
            return null;
        });
    }

    /** Выполняет действие от имени workspace и возвращает результат. */
    public static <T> T callAs(UUID workspaceId, Supplier<T> action) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (SYSTEM.equals(workspaceId)) {
            throw new IllegalArgumentException("Системный идентификатор нельзя использовать как workspace");
        }
        return bind(workspaceId, action);
    }

    /** Выполняет действие в системном контексте (только outbox). */
    public static void runAsSystem(Runnable action) {
        Objects.requireNonNull(action, "action");
        callAsSystem(() -> {
            action.run();
            return null;
        });
    }

    /** Выполняет действие в системном контексте и возвращает результат. */
    public static <T> T callAsSystem(Supplier<T> action) {
        return bind(SYSTEM, action);
    }

    /** Текущий workspace. Пусто вне контекста и в системном контексте. */
    public static Optional<UUID> currentWorkspaceId() {
        UUID current = CURRENT.get();
        return SYSTEM.equals(current) ? Optional.empty() : Optional.ofNullable(current);
    }

    /** Текущий workspace или исключение, если контекста workspace нет. */
    public static UUID requireWorkspaceId() {
        return currentWorkspaceId()
                .orElseThrow(() -> new IllegalStateException("Контекст workspace не установлен"));
    }

    public static boolean isSystem() {
        return SYSTEM.equals(CURRENT.get());
    }

    /** Сырой идентификатор для резолвера Hibernate: workspace, {@link #SYSTEM} или null. */
    static UUID currentIdentifier() {
        return CURRENT.get();
    }

    private static <T> T bind(UUID identifier, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        UUID previous = CURRENT.get();
        if (previous != null && !previous.equals(identifier)) {
            throw new IllegalStateException("Смена тенанта внутри активного контекста запрещена");
        }
        CURRENT.set(identifier);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
