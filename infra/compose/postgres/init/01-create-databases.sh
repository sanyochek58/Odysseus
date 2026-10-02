#!/bin/bash
# Создаёт БД сервисов при первом старте тома. Выполняется entrypoint-ом postgres.
set -euo pipefail

for db in workspace_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
SELECT 'CREATE DATABASE ${db}' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${db}')\gexec
SQL
done
