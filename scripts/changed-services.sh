#!/usr/bin/env bash
# Печатает JSON-массив сервисов из settings.gradle, изменённых относительно BASE (по умолчанию origin/main).
# Изменения в libs затрагивают сервисы, зависящие от этой либы (`:libs:<имя>` в build.gradle сервиса).
# Изменения корневой сборки, каталога версий или CI затрагивают все сервисы.
set -eu
BASE=${1:-origin/main}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
all=$(sed -nE "s/^include ':services:([a-z-]+)'.*/\1/p" backend/settings.gradle)
files=$(git diff --name-only "$BASE"...HEAD)
if printf '%s\n' "$files" | grep -qE '^(backend/(build\.gradle|settings\.gradle|gradle/)|\.github/workflows/|scripts/changed-services\.sh)'; then
  sel=$all
else
  sel=
  for s in $all; do
    if printf '%s\n' "$files" | grep -q "^backend/services/$s/"; then sel="$sel $s"; continue; fi
    for l in $(printf '%s\n' "$files" | sed -nE 's#^backend/libs/([^/]+)/.*#\1#p' | sort -u); do
      if grep -qs ":libs:$l'" "backend/services/$s/build.gradle"; then sel="$sel $s"; break; fi
    done
  done
fi
out=
for s in $sel; do out="$out\"$s\","; done
echo "[${out%,}]"
