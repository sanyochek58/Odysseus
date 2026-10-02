# Outbox и консьюмеры

Реализация живёт один раз в `libs/common-events` (auto-configuration). Сервисы её подключают, не переписывают.

Таблицы в БД каждого сервиса (changeset-шаблон поставляется библиотекой):
- `outbox`: id, topic, aggregate_id, workspace_id, payload (jsonb), created_at, sent_at.
- `processed_events`: event_id, consumer, processed_at, уникальность по (event_id, consumer).

Отправка:
- Бизнес-код внутри своей `@Transactional` вызывает `OutboxService.save(topic, aggregateId, event)`.
- Публикатор `@Scheduled` выбирает неотправленные строки `ORDER BY created_at LIMIT n FOR UPDATE SKIP LOCKED`, шлёт в Kafka с ключом `aggregateId`, ставит `sent_at`. Доставка at-least-once, при повторных репликах дублей нет.
- Публикатор работает в системном контексте тенанта и трогает только таблицу outbox.

Приём:
- Консьюмер в одной транзакции проверяет `processed_events`, обрабатывает событие, записывает `eventId`. Дубликат пропускается.
- Тенант берётся из `event.workspaceId()` (`docs/guides/tenancy.md`).
- Ошибка: retry с бэкоффом, затем `<topic>.dlt`.

События: records в `common-events` с полями `eventId, occurredAt, workspaceId, version`. Менять только добавлением полей.
