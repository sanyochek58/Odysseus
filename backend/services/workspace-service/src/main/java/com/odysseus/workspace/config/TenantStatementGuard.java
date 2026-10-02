package com.odysseus.workspace.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Запрещает любой SQL через Hibernate вне контекста тенанта (HTTP, {@code runAs} или {@code runAsSystem}).
 * Открыть сессию без контекста можно, выполнить запрос нельзя.
 */
public class TenantStatementGuard implements StatementInspector {

    @Override
    public String inspect(String sql) {
        if (TenantContext.currentIdentifier() == null) {
            throw new IllegalStateException("Обращение к БД вне контекста тенанта");
        }
        return sql;
    }
}
