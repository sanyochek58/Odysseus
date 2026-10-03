# Outbox и консьюмеры

Реализация живёт один раз в `libs/common-events` (auto-configuration `OutboxAutoConfiguration`). Сервисы её подключают, не переписывают.

Подключение в сервисе:
1. `implementation project(":libs:common-events")` в `build.gradle`.
2. Таблицы: в master-changelog сервиса `include: file: db/changelog/common-events/outbox-changelog.yaml` (outbox и processed_events с rollback). Применённые changesets в БД сервиса не редактировать.
3. Бин `TenantScope` (`runAsSystem`, `runAs(workspaceId, ...)`), делегирующий в тенантный контекст сервиса (пример: `workspace-service/config/TenantScopeConfig`). Без него тенант не привязывается.
4. Параметры: `odysseus.outbox.enabled` (true), `batch-size` (100), `poll-interval-ms` (1000), `retry.max-attempts` (3), `retry.initial-interval-ms` (1000), `retry.multiplier` (2.0), `retry.max-interval-ms` (30000). Kafka: `spring.kafka.*`, продюсер со String-сериализаторами.
5. Пакет сущности `Outbox` добавляется в сканирование JPA автоматически (`@AutoConfigurationPackage`); если сервис задаёт `@EntityScan`/`@EnableJpaRepositories` явно, добавить `com.odysseus.events.outbox.store`.

Таблицы в БД каждого сервиса (changeset-шаблон поставляется библиотекой):
- `outbox`: id, topic, aggregate_id, workspace_id, payload (jsonb), created_at, sent_at.
- `processed_events`: event_id, consumer, processed_at, уникальность по (event_id, consumer).

Отправка:
- Бизнес-код внутри своей `@Transactional` вызывает `OutboxService.save(topic, aggregateId, event)` (event реализует `DomainEvent`) либо `save(topic, aggregateId, workspaceId, eventId, payload)`. Вне транзакции вызов падает (MANDATORY).
- Публикатор `@Scheduled` выбирает неотправленные строки `ORDER BY created_at LIMIT n FOR UPDATE SKIP LOCKED`, шлёт в Kafka с ключом `aggregateId`, ставит `sent_at`. Доставка at-least-once, при повторных репликах дублей нет.
- Публикатор работает в системном контексте тенанта и трогает только таблицу outbox.

Приём:
- Слушатель (отдельный бин) вызывает `IdempotentEventProcessor.process(consumerName, event, handler)`: тенант из `event.workspaceId()` выставляется до транзакции, в одной транзакции `INSERT ... ON CONFLICT DO NOTHING` в `processed_events` и обработчик. Дубликат пропускается (возврат `false`), ошибка обработчика откатывает всё.
- Тенант берётся из `event.workspaceId()` (`docs/guides/tenancy.md`).
- Ошибка: `DefaultErrorHandler` из автоконфигурации (экспоненциальный бэкофф, затем `DeadLetterPublishingRecoverer` в `<topic>.dlt`, строчными). Свой `CommonErrorHandler` бин его заменяет. Топик `.dlt` должен существовать или создаваться брокером.

События: records в `common-events`, реализуют `DomainEvent`, с полями `eventId, occurredAt, workspaceId, version`. Менять только добавлением полей.
