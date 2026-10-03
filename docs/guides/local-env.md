# Локальное окружение

Порядок:
1. `cp infra/compose/.env.example infra/compose/.env`, при необходимости поправить значения (файл не коммитится).
2. `make up`, подождать старта keycloak (до минуты).
3. `make realm-check`: realm `odysseus` импортирован, токен `test-user` выдаётся клиентом `odysseus-dev` (direct grant, только dev-клиент), в токене есть `sub`, `email`, `email_verified=true`, `aud` (scope `odysseus-api`) и claim `organization` с id. Пароль берётся из `KEYCLOAK_TEST_USER_PASSWORD`, токен и пароль не печатаются.
4. Запуск сервиса: `make run SVC=workspace-service` (на переднем плане) или `make run-bg SVC=workspace-service` (в фоне, ждёт health; остановка `make stop SVC=...`). Переменные БД, Kafka и issuer подставляются из `.env`. Если порт 5432 занят, задайте `POSTGRES_PORT` в `.env`.
5. `make smoke SVC=workspace-service`: health 200, без токена 401, токен `test-user` → `POST /api/v1/workspaces` (201 или 409 при повторе), `GET` списка и своего workspace 200, чужой id 404 или 403.
6. `make down`.

Пароль test-user применяется только при импорте realm. Если сменили его в `.env`, пересоздайте keycloak: `make down`, `make up`.

## Сервис в контейнере (профиль app)

1. `make image SVC=workspace-service` (bootJar, затем многоэтапный Dockerfile: JRE 25, non-root, healthcheck через `/actuator/health`).
2. `make up PROFILE=app` поднимает окружение и workspace-service на порту 8081 (`docs/ports.md`); `make down` останавливает всё, включая профиль.
3. Внутри сети compose сервис ходит в `postgres:5432` и `kafka:19092`. Issuer для контейнера `http://host.docker.internal:8180/realms/odysseus` (переменная `APP_KEYCLOAK_ISSUER_URI`). Dev-keycloak берёт `iss` из Host запроса, поэтому токен для такого сервиса нужно получать по тому же хосту: добавьте `127.0.0.1 host.docker.internal` в `/etc/hosts` (на Docker Desktop запись уже есть) и запрашивайте токен на `http://host.docker.internal:8180`. Для обычной разработки используйте `make run`.

## CI и k8s

- PR: джобы `Сборка и юнит-тесты`, `Интеграционные тесты, Testcontainers` и `Docker-образ` на каждый изменённый сервис (`make changed-services`; изменения в `backend/libs`, корневой сборке и CI затрагивают все сервисы). Push образа в registry не настроен.
- k8s: `infra/k8s/base` (kustomize). Secret `workspace-service-secrets` создаётся вне git по шаблону `workspace-service-secret.template.yaml` (ключи `DB_USERNAME`, `DB_PASSWORD`). Образ и адреса зависимостей в ConfigMap заданы по допущению и переопределяются оверлеем.
- Порт для `make stop` и других скриптов берётся из `docs/ports.md` (`scripts/lib-ports.sh`), строки вида `- <service> <порт>`.
