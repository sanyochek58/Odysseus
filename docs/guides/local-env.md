# Локальное окружение

Порядок:
1. `cp infra/compose/.env.example infra/compose/.env`, при необходимости поправить значения (файл не коммитится).
2. `make up`, подождать старта keycloak (до минуты).
3. `make realm-check`: realm `odysseus` импортирован, токен `test-user` выдаётся клиентом `odysseus-dev` (direct grant, только dev-клиент), в токене есть `sub`, `email`, `email_verified=true`, `aud` (scope `odysseus-api`) и claim `organization` с id. Пароль берётся из `KEYCLOAK_TEST_USER_PASSWORD`, токен и пароль не печатаются.
4. Запуск сервиса: `make run SVC=workspace-service` (на переднем плане) или `make run-bg SVC=workspace-service` (в фоне, ждёт health; остановка `make stop SVC=...`). Переменные БД, Kafka и issuer подставляются из `.env`. Если порт 5432 занят, задайте `POSTGRES_PORT` в `.env`.
5. `make smoke SVC=workspace-service`: health 200, без токена 401, токен `test-user` → `POST /api/v1/workspaces` (201 или 409 при повторе), `GET` списка и своего workspace 200, чужой id 404 или 403.
6. `make down`.

Пароль test-user применяется только при импорте realm. Если сменили его в `.env`, пересоздайте keycloak: `make down`, `make up`.
