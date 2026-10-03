package com.odysseus.events;

import java.util.UUID;

/**
 * Привязка тенанта к потоку. Библиотека не знает про механизм тенантности сервиса: сервис
 * объявляет бин, делегирующий в свой {@code TenantContext}. Без бина работает {@link #NONE}.
 */
public interface TenantScope {

    /** Без привязки тенанта: сервисы без тенантного резолвера и тесты. */
    TenantScope NONE = new TenantScope() {
        @Override
        public void runAsSystem(Runnable action) {
            action.run();
        }

        @Override
        public void runAs(UUID workspaceId, Runnable action) {
            action.run();
        }
    };

    /** Системный контекст: публикатор outbox, трогает только таблицу outbox. */
    void runAsSystem(Runnable action);

    /** Контекст workspace из события для консьюмера. */
    void runAs(UUID workspaceId, Runnable action);
}
