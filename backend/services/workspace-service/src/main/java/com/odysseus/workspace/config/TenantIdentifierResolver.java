package com.odysseus.workspace.config;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Отдаёт Hibernate текущий тенант для {@code @TenantId}.
 * Без контекста отдаёт {@link TenantContext#NO_TENANT}: Spring Data открывает EntityManager при старте вне HTTP,
 * и само открытие сессии не должно падать. Данных под этим id нет, а любой SQL без контекста запрещает
 * {@link TenantStatementGuard}, запись с чужим или служебным тенантом запрещает {@link TenantWriteGuard}.
 * Корневого тенанта нет: ни системный контекст, ни отсутствие контекста не снимают фильтр по тенанту.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID identifier = TenantContext.currentIdentifier();
        return identifier == null ? TenantContext.NO_TENANT : identifier;
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
