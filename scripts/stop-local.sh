#!/usr/bin/env bash
# Останавливает сервис, запущенный make run-bg (gradle и дочерний java).
SVC=${1:?SVC не задан}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PIDF="$ROOT/backend/services/$SVC/build/run-local.pid"
[ -f "$PIDF" ] || { echo "нет pid, $SVC не запущен через make run-bg"; exit 0; }
pid=$(cat "$PIDF")
pkill -P "$pid" 2>/dev/null; kill "$pid" 2>/dev/null
# bootRun порождает java-процесс глубже одного уровня
pkill -f "com.odysseus.*$(echo "$SVC" | sed 's/-service//')" 2>/dev/null
rm -f "$PIDF"; echo "ok $SVC остановлен"
