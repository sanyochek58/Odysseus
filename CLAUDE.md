# Odysseus CRM

CRM для малых IT-компаний. Продаётся по подписке: компания (workspace) покупает доступ на срок.
Разработчики: 2 человека, Java backend. Реальный продукт, поэтому качество, тесты и безопасность обязательны.

## Правила работы (все агенты)

- Делай ровно поставленную задачу. Чужие сервисы, `backend/libs/`, `infra/`, `.github/`, чужие миграции не трогай без задачи. Баг вне задачи только в отчёт.
- Критерии приёмки в задаче и есть утверждённый план, работай сразу. При неясности сделай минимальное допущение и укажи его в коммите. Останавливайся только если ошибка потянет переделку схемы БД, публичного API или безопасности.
- Зависимости и версии не добавлять без разрешения. Версии только в `gradle/libs.versions.toml`.
- Все команды только через `make` (`make help`). Gradle и docker напрямую не вызывать.
- Не перечитывай файлы целиком, ищи через Grep. Не повторяй прогон без изменений кода.
- API и версии Spring Boot 4, Jackson 3, Testcontainers сверяй через Context7, не по памяти.
- Оркестратор ничего не редактирует и не запускает, только делегирует.
- Git: ветка `feature/<service>-<кратко>` или `fix/<service>-<кратко>`, коммиты Conventional Commits на русском, только файлы задачи. Не пушить, `main` не трогать.

## Отчёты

Субагент оркестратору одной строкой:
`OK <ветка>@<хеш> | суть до 10 слов`, либо `PART`, `FAIL`, либо `BLOCK <AMBIG|SCOPE|DEP|ENV> <до 15 слов>`.
Баг вне задачи в ту же строку: `+BUG путь:строка слова`. Подробности в сообщении коммита.
Оркестратор человеку: до 5 строк, что сделано и что нужно от человека. Без заголовков, таблиц, вступлений и пересказа задачи, жирным только ключевое слово.
Все: без воды и извинений, без markdown-украшений.

## Сервисы

workspace-service 8081 (workspace, участники, роли, приглашения, подписка), project-service 8082 (проекты, команда проекта), task-service 8083 (канбан: доски, колонки, задачи, комментарии), calendar-service 8084 (встречи, события, приглашения). В сборке только они.
Позже: document-service (MinIO), ai-service (облачный LLM). Не реализовывать: mail, call, infra, payment.
Аутентификацию делает Keycloak (realm `odysseus`). Своих логинов, паролей и выдачи JWT не писать.
У каждого сервиса свой `CLAUDE.md` (домен, API, события, до 100 строк), читай его первым. Чужие сервисы изучай только по их `CLAUDE.md`, не по коду.

## Стек

Java 25, Spring Boot 4.x (Jackson 3, `@MockitoBean` вместо `@MockBean`), Gradle Groovy + version catalog, Spring Web MVC (виртуальные потоки), Data JPA, Security OAuth2 Resource Server, PostgreSQL 17, Liquibase YAML, Kafka KRaft + Spring Kafka + Outbox, MapStruct, Lombok, springdoc, Actuator + Micrometer, JUnit 5, AssertJ, Mockito, Testcontainers. Инфра: Docker, compose, k3s, Terraform, Ansible, nginx, GitHub Actions.
MapStruct и Lombok: порядок процессоров в корневом `build.gradle` не менять.
Между сервисами: Kafka основной, REST (`RestClient`) только когда без ответа не обойтись. gRPC пока не использовать.

## Структура

```
backend/  settings.gradle, build.gradle, gradle/libs.versions.toml, libs/{common-web,common-security,common-events}, services/<name>-service
frontend/  infra/{compose,k8s,nginx,keycloak,terraform,ansible}  docs/{adr,guides}  .github/workflows
```
- В `libs/` только контракты событий, outbox и инфраструктурный конфиг web/security. Сущностей, репозиториев и бизнес-логики там нет.
- Сервис зависит только от `libs/*`, не от другого сервиса.
- Слои: `controller → service → repository`. `@Transactional` только в `service`. Сущности не выходят из `service`, наружу DTO. Пакеты: controller, service, repository, entity, dto, mapper, event, exception, config.

## Код

- Идентификаторы на английском, комментарии и `@DisplayName` на русском.
- DTO это `record` с `jakarta.validation`, в контроллере `@Valid`.
- Зависимости через конструктор (`@RequiredArgsConstructor`), `@Autowired` на полях нет.
- Lombok разрешён весь, кроме `@Data`. Сущности: `@Getter @Setter @Builder @NoArgsConstructor(access = PROTECTED) @AllArgsConstructor(access = PRIVATE)`, `equals/hashCode` по `id`, связи `LAZY`.
- ID это `UUID`, время `Instant`/`timestamptz`, у сущностей `createdAt` и `updatedAt`.
- API: `/api/v1/<существительные во мн. числе>`, списки с `Pageable`. Ошибки только RFC 9457 `ProblemDetail` из `common-web`, без стектрейсов.
- Логи JSON без персональных данных и токенов. Секреты только из окружения или k8s Secret, в коде и тестах их нет.

## Мультитенантность

- Тенант это workspace. Во всех тенантных таблицах `workspace_id UUID NOT NULL`, индексы начинаются с него.
- `workspaceId` только из JWT (Keycloak Organizations) через `common-security`. Никогда из тела, query, path и заголовков вроде `X-User-Id`.
- Hibernate `@TenantId` + `CurrentTenantIdentifierResolver`. Нативный SQL без условия по тенанту запрещён.
- Вне HTTP (консьюмеры Kafka, планировщик) тенант берётся из события или системного контекста: `docs/guides/tenancy.md`.
- На каждую фичу тест: пользователь workspace A не видит и не меняет данные B.
- Подписка: после `expiresAt` запись даёт 403 `ProblemDetail`. Сервисы блокируют запись по локальной копии из события `workspace.subscription.changed` (`docs/adr/001-subscription-projection.md`). Платежи не реализовывать.
- Роли `OWNER, ADMIN, TECH_LEAD, MANAGER, MEMBER`, проверка `@PreAuthorize`. Новые роли только по явной задаче.

## БД и события

- Один инстанс Postgres, своя БД на сервис, в чужую не ходить.
- Схема только через Liquibase YAML, файл `NNN-описание.yaml`, у каждого changeset `rollback`, применённые не редактировать. `ddl-auto=validate`.
- Топики `<service>.<entity>.<event>`. Контракты это `record` в `common-events` (`eventId, occurredAt, workspaceId, version`), менять только добавлением полей.
- Отправка только через outbox, прямой `kafkaTemplate.send()` из бизнес-кода запрещён. Консьюмеры идемпотентны, ошибки retry → `<topic>.dlt`. Детали: `docs/guides/outbox.md`.

## Тесты

- Во время работы только юнит затронутого класса: `make test-class`. В конце один раз `make build`. Интеграционные (Testcontainers) только `make it`, их запускает tester или CI, разработчик не запускает.
- Бизнес-логика в `service` на Mockito, контроллеры `@WebMvcTest`, JWT в тестах поддельный. Реальный Keycloak только в сквозных тестах. H2 нельзя.
- Покрытие JaCoCo 80% только на пакет `service`.
- Имя теста `метод_условие_ожидание`, `@DisplayName` на русском. Подробности: `docs/guides/testing.md`.

## Ловушки

- Личность только из проверенного JWT.
- Повторная отправка той же операции даёт 409 (уникальный индекс или ключ идемпотентности).
- `@Transactional` без вызова через `this`, обработчики событий отдельным бином.
- Диапазоны и инварианты проверяются на сервере.
- Секретов по умолчанию нет, prod-профиль падает без секрета.
- Liquibase: пути от master-файла, применённое не править. compose без `container_name`, порты по `docs/ports.md`.
- Security-код (`common-security`, резолвер тенанта, авторизация) правит только `security-dev`. Диагностику сложных ошибок не отдавать `helper`.

## Готово, когда

1. `make build SVC=<service>` зелёный.
2. `CLAUDE.md` сервиса обновлён, если менялись API или события.
3. Коммит в ветке, отчёт по протоколу.
