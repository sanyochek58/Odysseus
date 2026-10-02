#!/usr/bin/env bash
# Останавливает сервис, запущенный make run-bg: только процессы из run-local.pid и их потомки.
SVC=${1:?SVC не задан}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PIDF="$ROOT/backend/services/$SVC/build/run-local.pid"
case "$SVC" in workspace-service) PORT=8081 ;; *) PORT= ;; esac
[ -f "$PIDF" ] || { echo "нет pid, $SVC не запущен через make run-bg"; exit 0; }
pid=$(tr -dc '0-9' < "$PIDF")

descendants() { local c; for c in $(pgrep -P "$1" 2>/dev/null); do descendants "$c"; echo "$c"; done; }

if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
  cmd=$(ps -o command= -p "$pid" 2>/dev/null)
  case "$cmd" in
    *gradlew*|*gradle*)
      pids="$(descendants "$pid") $pid"
      # shellcheck disable=SC2086
      kill $pids 2>/dev/null ;;
    *) echo "pid $pid не наш процесс ($cmd), не трогаю"; rm -f "$PIDF"; exit 0 ;;
  esac
else
  echo "процесс $pid уже не жив"
fi
# bootRun форкается gradle-демоном, а не клиентом: добиваем java, слушающую порт, только если это наш сервис
if [ -n "$PORT" ]; then
  for p in $(lsof -ti "tcp:$PORT" -sTCP:LISTEN 2>/dev/null); do
    c=$(ps -o command= -p "$p" 2>/dev/null)
    case "$c" in *"$ROOT/backend/services/$SVC/"*|*"$ROOT/backend/"*"$SVC"*) kill "$p" 2>/dev/null ;; esac
  done
fi
rm -f "$PIDF"; echo "ok $SVC остановлен"
