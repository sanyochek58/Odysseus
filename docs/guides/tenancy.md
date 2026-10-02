# Тенантность

HTTP: `common-security` берёт `workspaceId` из claim организации Keycloak, кладёт в `TenantContext`, `CurrentTenantIdentifierResolver` отдаёт его Hibernate. Тенантные сущности помечены `@TenantId`. Сама сущность Workspace фильтруется по `id` = тенант.

Вне HTTP:
- Консьюмер Kafka: `TenantContext.runAs(event.workspaceId(), () -> ...)`. События внутренние, им доверяем.
- Планировщик outbox: `TenantContext.runAsSystem(...)`, только для таблицы outbox. Читать бизнес-сущности в системном контексте нельзя.

Правила:
- Нативный SQL обязан содержать `workspace_id = :tenant`.
- Идентичность и `workspaceId` из заголовков, тела и параметров не принимаются.
- Чужой ресурс неотличим от несуществующего: ответ 404, не 403.
- Тест изоляции на каждую фичу: два workspace, пользователь A не читает, не меняет, не удаляет данные B.

Подписка: `workspace-service` публикует `workspace.subscription.changed`. Каждый сервис хранит `workspace_subscription(workspace_id, expires_at)` и блокирует запись фильтром (403 `ProblemDetail`).
