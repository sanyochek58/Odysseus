#!/usr/bin/env bash
# Смоук сервиса: health 200, без токена 401, с токеном test-user 200 или 403. Токен и пароль не печатаются.
set -u
SVC=${1:?SVC не задан}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/lib-env.sh"
KC=${KEYCLOAK_URL:-http://localhost:8180}
case "$SVC" in
  workspace-service) PORT=8081 ;;
  project-service)   PORT=8082 ;;
  task-service)      PORT=8083 ;;
  calendar-service)  PORT=8084 ;;
  *) echo "FAIL неизвестный сервис $SVC (порты: docs/ports.md)"; exit 1 ;;
esac
BASE=http://localhost:$PORT
PATH_API=/api/v1/workspaces
[ "$SVC" = workspace-service ] || { echo "FAIL smoke пока описан только для workspace-service"; exit 1; }
fail=0
check() { # имя, ожидаемые коды (через |), фактический, пояснение
  if [[ "|$2|" == *"|$3|"* ]]; then echo "ok   $1: $3 $4"; else echo "FAIL $1: $3, ожидалось $2"; fail=1; fi
}
h=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health") || h=000
check "health" 200 "$h" ""
n=$(curl -s -o /dev/null -w '%{http_code}' "$BASE$PATH_API") || n=000
check "без токена" 401 "$n" ""
resp=$(curl -s -X POST "$KC/realms/odysseus/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=odysseus-dev -d username=test-user \
  --data-urlencode "password=$KEYCLOAK_TEST_USER_PASSWORD")
tok=$(printf '%s' "$resp" | jq -r '.access_token // empty')
[ -n "$tok" ] || { echo "FAIL токен не получен (make realm-check)"; exit 1; }
t=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $tok" "$BASE$PATH_API") || t=000
check "с токеном test-user" "200|403" "$t" "(200: есть членство в workspace; 403: токен валиден, но test-user ещё не участник workspace, список требует членства)"
exit $fail
