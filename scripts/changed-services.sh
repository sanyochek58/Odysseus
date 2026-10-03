#!/usr/bin/env bash
# Печатает JSON-массив сервисов из settings.gradle, изменённых относительно BASE (по умолчанию origin/main).
# Изменения в libs затрагивают сервисы, зависящие от этой либы (`:libs:<имя>` в build.gradle сервиса).
# Зависимость учитывается транзитивно (либа от либы).
# Изменения корневой сборки, gradle.properties, wrapper, каталога версий, Makefile, этих скриптов или CI затрагивают все сервисы.
set -eu
BASE=${1:-origin/main}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
all=$(sed -nE "s/^include ':services:([a-z-]+)'.*/\1/p" backend/settings.gradle)
files=$(git diff --name-only "$BASE"...HEAD)
if printf '%s\n' "$files" | grep -qE '^(backend/(build\.gradle|settings\.gradle|gradle\.properties|gradlew|gradle/)|Makefile|scripts/changed-(services|libs)\.sh|\.github/workflows/)'; then
  sel=$all
else
  # зависит ли каталог $1 (build.gradle) от либы $2: любые кавычки и формы project(':libs:x'), project(path: ":libs:x")
  dep() { grep -qsE ":libs:$2([^A-Za-z0-9_-]|$)" "$1/build.gradle"; }
  # затронутые либы: изменённые плюс транзитивно зависящие от них (замыкание до неподвижной точки)
  aff=$(printf '%s\n' "$files" | sed -nE 's#^backend/libs/([^/]+)/.*#\1#p' | sort -u)
  while :; do
    n=$aff
    for l in $(ls backend/libs); do
      for a in $aff; do
        if [ "$a" != "$l" ] && dep "backend/libs/$l" "$a"; then n=$(printf '%s\n%s\n' "$n" "$l"); break; fi
      done
    done
    n=$(printf '%s\n' "$n" | sed '/^$/d' | sort -u)
    [ "$n" = "$(printf '%s\n' "$aff" | sed '/^$/d' | sort -u)" ] && break
    aff=$n
  done
  sel=
  for s in $all; do
    if printf '%s\n' "$files" | grep -q "^backend/services/$s/"; then sel="$sel $s"; continue; fi
    for l in $aff; do
      if dep "backend/services/$s" "$l"; then sel="$sel $s"; break; fi
    done
  done
fi
out=
for s in $sel; do out="$out\"$s\","; done
echo "[${out%,}]"
