#!/usr/bin/env bash
# Проверка realm odysseus: импорт, токен test-user через odysseus-dev (direct grant), claim organization.
# Пароль из infra/compose/.env, токен и пароль не печатаются.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/lib-env.sh"
KC=${KEYCLOAK_URL:-http://localhost:8180}

# keycloak стартует до минуты, ждём до WAIT секунд (по умолчанию 90)
code=000
for _ in $(seq 1 $(( ${WAIT:-90} / 3 + 1 ))); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$KC/realms/odysseus/.well-known/openid-configuration") || code=000
  [ "$code" = 200 ] && break
  sleep 3
done
[ "$code" = 200 ] || { echo "FAIL realm odysseus не найден ($KC, HTTP $code): make up, подождите старта keycloak"; exit 1; }
echo "ok realm odysseus импортирован"

resp=$(curl -s -w '\n%{http_code}' -X POST "$KC/realms/odysseus/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=odysseus-dev -d username=test-user \
  --data-urlencode "password=$KEYCLOAK_TEST_USER_PASSWORD") || true
status=${resp##*$'\n'}
[ "$status" = 200 ] || { echo "FAIL токен test-user: HTTP $status (пароль в .env должен совпадать с импортом realm, при смене пересоздайте keycloak: make down, make up)"; exit 1; }
echo "ok токен test-user получен"

org=$(printf '%s' "${resp%$'\n'*}" | jq -r '.access_token' | cut -d. -f2 | tr '_-' '/+' \
  | { read -r p; while [ $(( ${#p} % 4 )) -ne 0 ]; do p="$p="; done; printf '%s' "$p" | base64 -d 2>/dev/null; } \
  | jq -r '.organization | if type=="object" and length==1 then (to_entries[0].value.id // empty) else empty end')
[ -n "$org" ] || { echo "FAIL в токене нет claim organization вида {alias: {id}} (ровно одна организация)"; exit 1; }
echo "ok claim organization, id: $org"
