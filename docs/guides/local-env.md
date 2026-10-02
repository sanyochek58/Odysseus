# Локальное окружение

Порядок:
1. `cp infra/compose/.env.example infra/compose/.env`, при необходимости поправить значения (файл не коммитится).
2. `make up`, подождать старта keycloak (до минуты).
3. `make realm-check`: realm `odysseus` импортирован, токен `test-user` выдаётся клиентом `odysseus-dev` (direct grant, только dev-клиент), в токене есть claim `organization`. Пароль берётся из `KEYCLOAK_TEST_USER_PASSWORD`, токен и пароль не печатаются.
4. Запуск сервиса: `make run SVC=workspace-service`.
5. `make smoke SVC=workspace-service`: `/actuator/health` 200, `GET /api/v1/workspaces` без токена 401, с токеном `test-user` 200 или 403 (403 если test-user ещё не участник workspace, токен при этом валиден).
6. `make down`.

Пароль test-user применяется только при импорте realm. Если сменили его в `.env`, пересоздайте keycloak: `make down`, `make up`.
