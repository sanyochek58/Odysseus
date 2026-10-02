package com.odysseus.workspace.config;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Отдаёт Hibernate текущий тенант для {@code @TenantId}.
 * Без контекста бросает исключение: доступ к БД вне {@link TenantContext} запрещён.
 * Системный контекст не корневой: фильтр по тенанту остаётся, бизнес-сущности в нём не видны.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID identifier = TenantContext.currentIdentifier();
        if (identifier == null) {
            throw new IllegalStateException("Обращение к БД вне контекста тенанта");
        }
        return identifier;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return false;
    }
}
