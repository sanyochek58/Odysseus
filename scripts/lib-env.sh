#!/usr/bin/env bash
# Загружает infra/compose/.env и проверяет обязательные переменные. Подключается через source.
ENV_FILE="${ENV_FILE:-$ROOT/infra/compose/.env}"
[ -f "$ENV_FILE" ] || { echo "FAIL нет $ENV_FILE (шаблон infra/compose/.env.example)"; exit 1; }
set -a; . "$ENV_FILE"; set +a
: "${KEYCLOAK_TEST_USER_PASSWORD:?KEYCLOAK_TEST_USER_PASSWORD не задан в .env}"
command -v jq > /dev/null || { echo "FAIL нужен jq"; exit 1; }
