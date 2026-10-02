# workspace-service (порт 8081)

Домен: workspace (тенант), участники и роли, приглашения, подписка (срок доступа). БД `workspace_db`.
Безопасность и тенантность: пакет `config` (JWT Keycloak, `TenantContext`, `@TenantId`-резолвер, блокировка записи по подписке).

## Модель
- `Workspace`: id равен workspaceId из JWT (claim organization), name.
- `Member`: userId (sub), email, role (OWNER, ADMIN, TECH_LEAD, MANAGER, MEMBER). Уникален (workspace_id, user_id). Всегда остаётся хотя бы один OWNER (проверка под PESSIMISTIC_WRITE на строках OWNER).
- `Invitation`: email (lower-case), role (не OWNER), status PENDING/ACCEPTED/REVOKED, срок 7 дней. Один PENDING на email.
- `Subscription`: одна на workspace, expiresAt. Пробный срок 30 дней создаётся вместе с workspace.
- `outbox`, `processed_events`: по `docs/guides/outbox.md`, реализованы внутри сервиса (в `libs/common-events` пусто).

Все тенантные сущности с `@TenantId workspaceId`; `Outbox` без него (читается планировщиком в системном контексте).

## API (`/api/v1`, JWT обязателен, ошибки ProblemDetail)
- JWT: issuer `KEYCLOAK_ISSUER_URI`, claim `aud` должен содержать `KEYCLOAK_AUDIENCE` (по умолчанию `odysseus-api`), иначе 401; пустое значение роняет старт.
- `POST /workspaces` (любой аутентифицированный с токеном организации, @SubscriptionNotRequired): регистрирует workspace организации из токена; 201, 409 если уже есть. Создатель становится OWNER (запись Member), создаётся пробная подписка.
- `GET /workspaces`, `GET /workspaces/{id}` (любая роль), `PUT /workspaces/{id}` (OWNER, ADMIN). `{id}` лишь сверяется с тенантом, чужой это 404.
- `GET /members`, `GET /members/{id}` (любая роль); `PUT /members/{id}/role` (OWNER); `DELETE /members/{id}` (OWNER, ADMIN; участника с ролью OWNER удаляет только OWNER, иначе 403). Последнего OWNER понизить или удалить нельзя: 409.
- `POST /invitations`, `GET /invitations`, `DELETE /invitations/{id}` (отзыв) (OWNER, ADMIN). Дубликат или уже участник: 409.
- `POST /invitations/{id}/accept` (любой пользователь с токеном этой организации): нужен `email_verified = true` (иначе 403), email токена должен совпасть с приглашённым (иначе 403, проверяется до статуса, чтобы не раскрывать его), затем статус и срок (иначе 409). Создаёт Member.
- `GET /subscriptions/current` (любая роль); `POST /subscriptions/current/extensions` `{days: 1..366}` (OWNER, @SubscriptionNotRequired): продление от max(now, expiresAt), без платежей. Заголовок `Idempotency-Key` обязателен (нет или пусто: 400, до 128 символов); ключ в `subscription_extension_key`, уникален (workspace_id, idempotency_key); повтор: 409, второго продления нет (гонка решается индексом).
- Списки с `Pageable`; ответ `{content: [...], page: {size, number, totalElements, totalPages}}` (`PageSerializationMode.VIA_DTO`). Чужой ресурс это 404, не 403.

## События (Kafka через outbox)
- `workspace.subscription.changed` (key = workspaceId): `SubscriptionChangedEvent(eventId, occurredAt, workspaceId, version=1, expiresAt)`, пакет `event`. Публикуется при создании workspace и продлении. Потребители держат копию `workspace_subscription(workspace_id, expires_at)` (ADR-001).
- Публикатор `OutboxPublisher` (@Scheduled, системный контекст) через `OutboxRelay`: `FOR UPDATE SKIP LOCKED`, at-least-once. Параметры `odysseus.outbox.batch-size`, `odysseus.outbox.poll-interval-ms`. Kafka: `KAFKA_BOOTSTRAP_SERVERS`.
- Консьюмеров нет; таблица `processed_events` создана для будущих.

## Допущения
- Организация Keycloak создаётся вне сервиса; `POST /workspaces` регистрирует её как workspace. Вступление пользователя в организацию Keycloak выдаётся отдельно, приглашение лишь фиксирует роль.
- Роль берётся из `Member.role` для (workspace из JWT, sub): `TenantContextFilter` через `MemberRolePort`, одна выборка на запрос. `realm_access.roles` игнорируются. Нет записи Member: ролей нет, защищённые операции 403.
- Первый OWNER: тот пользователь организации, кто первым вызвал `POST /workspaces`.
- Веб-тесты контроллеров: `@WebMvcTest(<Controller>.class)` + `@Import` конфигов (`ApiTestBase`), сервисы `@MockitoBean`, JWT через `jwt()`.
