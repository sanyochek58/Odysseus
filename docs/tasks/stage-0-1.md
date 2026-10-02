# Стартовая задача: workspace-service с Keycloak

Стартовый промпт для оркестратора (`make orchestrator`):

> Выполни задачи из `docs/tasks/stage-0-1.md`. Критерии приёмки ниже и есть утверждённый план. Независимые задачи запускай параллельно.

Цель: один рабочий сервис `workspace-service` (порт 8081), защищённый через Keycloak. Остальные сервисы и `libs/` не делаем. Общий код (security, outbox, ProblemDetail) на этом этапе живёт внутри сервиса, выносить в `libs/` будем позже отдельной задачей.

Правила этапа: агенты только создают файлы и коммитят в свои ветки. Сборку, тесты, `make up` и любые проверки не запускают, всё проверяет человек. Пункт «Готово, когда» из `CLAUDE.md` не применяется. `main` не трогать.

Зависимости: T1 → T3. T2 независима.

## T1 infra-dev: сборка под один сервис
Ветка `feature/infra-gradle`.
- `backend/settings.gradle`: оставить только `:services:workspace-service`.
- `libs.versions.toml`: добавить OAuth2 Resource Server, Spring Kafka, MapStruct с `lombok-mapstruct-binding`, springdoc, Actuator, Testcontainers (postgresql, kafka, junit-jupiter), spring-security-test, JaCoCo. Версии сверить через Context7 под Spring Boot 4.1.
- Корневой `build.gradle`: порядок процессоров `lombok`, `lombok-mapstruct-binding`, `mapstruct-processor`, комментарий «порядок не менять».
- `test` исключает тег `integration`, задача `integrationTest` берёт только его, `build` её не вызывает. JaCoCo 80% только на пакет `service`.
- Зависимости в `workspace-service/build.gradle` через каталог.

## T2 infra-dev: compose, Keycloak и CI
Ветка `feature/infra-compose`.
- `infra/compose/docker-compose.yml`: PostgreSQL 17 (БД `workspace_db`, скрипт инициализации), Kafka KRaft, Keycloak с импортом realm `odysseus`: Organizations включены, роли `OWNER, ADMIN, TECH_LEAD, MANAGER, MEMBER`, тестовый клиент для разработки. Без `container_name`, порты по `docs/ports.md`, секреты из `.env`, рядом `.env.example`.
- `.github/workflows/ci.yml`: на PR шаги `make build SVC=workspace-service`, затем `make it SVC=workspace-service`, Java 25, кэш Gradle, без секретов.

## T3 backend-dev + security-dev: workspace-service (после T1)
Домен: workspace, участники, роли, приглашения, подписка. Задача явно разрешает security-код внутри этого сервиса.

Ветка `feature/workspace-security` (security-dev), пакет `config`:
- Resource Server, JWT из realm `odysseus`, `workspaceId` из claim организации Keycloak.
- `TenantContext` (`runAs`, `runAsSystem`), `CurrentTenantIdentifierResolver`, `@EnableMethodSecurity`, роли.
- Блокировка записи после `expiresAt` подписки: 403 `ProblemDetail`.
- Юнит-тесты на контекст и резолвер.

Ветка `feature/workspace-core` (backend-dev), все слои по `CLAUDE.md`:
- Сущности и Liquibase YAML с rollback: `Workspace`, `Member`, `Invitation`, `Subscription`, а также `outbox` и `processed_events` по `docs/guides/outbox.md`.
- API `/api/v1/workspaces`, участники, приглашения, продление подписки (без платежей).
- Публикация `workspace.subscription.changed` через outbox внутри сервиса (контракт события record в пакете `event`).
- ProblemDetail-обработчик ошибок, MapStruct, DTO records.
- Тесты: сервисы на Mockito, `@WebMvcTest` (успех, 400, 404, 403, 409), один тест маппера на пустые поля, тест изоляции тенантов.
- `CLAUDE.md` сервиса: домен, API, события.

Порядок: security-dev ветка первой, backend-dev берёт её как основу, если нужны `TenantContext` и роли. Конфликты решает `helper` по запросу оркестратора.

## Отчёт оркестратора человеку
До 5 строк: ветки с хешами и что проверить руками.
