#!/usr/bin/env bash
# Запуск сервиса против локального окружения (make up). Секреты из infra/compose/.env.
# Режим bg: в фоне, лог и pid в backend/services/<svc>/build/run-local.{log,pid}; ждёт health до 150 с.
set -u
SVC=${1:?SVC не задан}; MODE=${2:-fg}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/lib-env.sh"
: "${POSTGRES_USER:?}"; : "${POSTGRES_PASSWORD:?}"
case "$SVC" in
  workspace-service) PORT=8081; DB=workspace_db ;;
  *) echo "FAIL локальный запуск описан только для workspace-service"; exit 1 ;;
esac
export SERVER_PORT=$PORT
export DB_URL=${DB_URL:-jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/$DB}
export DB_USERNAME=$POSTGRES_USER DB_PASSWORD=$POSTGRES_PASSWORD
export KEYCLOAK_ISSUER_URI=${KEYCLOAK_ISSUER_URI:-http://localhost:8180/realms/odysseus}
export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
cd "$ROOT/backend" || exit 1
[ "$MODE" = bg ] || exec ./gradlew :services:"$SVC":bootRun
OUT="$ROOT/backend/services/$SVC/build"; mkdir -p "$OUT"
nohup ./gradlew --console=plain :services:"$SVC":bootRun > "$OUT/run-local.log" 2>&1 &
echo $! > "$OUT/run-local.pid"
for _ in $(seq 1 50); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/actuator/health")" = 200 ] && { echo "ok $SVC запущен, лог $OUT/run-local.log"; exit 0; }
  kill -0 "$(cat "$OUT/run-local.pid")" 2>/dev/null || break
  sleep 3
done
echo "FAIL $SVC не поднялся, хвост лога:"; tail -n 40 "$OUT/run-local.log"; exit 1
