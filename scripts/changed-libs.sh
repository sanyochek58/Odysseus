#!/usr/bin/env bash
# Печатает JSON-массив библиотек из backend/libs, изменённых относительно BASE (по умолчанию origin/main).
# Изменения корневой сборки, gradle.properties, wrapper, каталога версий, Makefile, этих скриптов или CI затрагивают все библиотеки.
set -eu
BASE=${1:-origin/main}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
all=$(ls backend/libs)
files=$(git diff --name-only "$BASE"...HEAD)
if printf '%s\n' "$files" | grep -qE '^(backend/(build\.gradle|settings\.gradle|gradle\.properties|gradlew|gradle/)|Makefile|scripts/changed-(services|libs)\.sh|\.github/workflows/)'; then
  sel=$all
else
  sel=
  for l in $all; do
    printf '%s\n' "$files" | grep -q "^backend/libs/$l/" && sel="$sel $l"
  done
fi
out=
for l in $sel; do out="$out\"$l\","; done
echo "[${out%,}]"
