#!/usr/bin/env bash
# Запускает команду, при успехе печатает одну строку, при ошибке хвост вывода.
# Нужен, чтобы агенты не тратили контекст на лог сборки.
set -u
LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT
if bash -c "$1" >"$LOG" 2>&1; then
  echo "OK"
else
  code=$?
  echo "FAIL (код $code), последние 60 строк:"
  tail -n 60 "$LOG"
  exit "$code"
fi
