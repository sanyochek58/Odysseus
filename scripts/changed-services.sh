#!/usr/bin/env bash
# Печатает JSON-массив сервисов из settings.gradle, изменённых относительно BASE (по умолчанию origin/main).
# Изменения в libs, корневой сборке или CI затрагивают все сервисы.
set -eu
BASE=${1:-origin/main}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
all=$(sed -nE "s/^include ':services:([a-z-]+)'.*/\1/p" backend/settings.gradle)
files=$(git diff --name-only "$BASE"...HEAD)
if printf '%s\n' "$files" | grep -qE '^(backend/(libs/|build\.gradle|settings\.gradle|gradle/)|\.github/workflows/|scripts/changed-services\.sh)'; then
  sel=$all
else
  sel=
  for s in $all; do
    printf '%s\n' "$files" | grep -q "^backend/services/$s/" && sel="$sel $s"
  done
fi
out=
for s in $sel; do out="$out\"$s\","; done
echo "[${out%,}]"
