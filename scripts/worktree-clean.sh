#!/usr/bin/env bash
# Очистка worktree агентов в .claude/worktrees/.
# Использование: scripts/worktree-clean.sh [имя-или-путь]
#   без аргумента: показать кандидатов и удалить все чистые worktree;
#   с аргументом: удалить один worktree.
# ВНИМАНИЕ: без аргумента не запускать, пока работают агенты:
# чистый worktree работающего агента тоже попадёт под удаление.
# Удаляются только worktree без незакоммиченных изменений (git worktree remove без --force).
# Ветка worktree-agent-* удаляется, только если её tip входит в feature/* или fix/*.
# feature/*, fix/*, main не удаляются никогда.
set -euo pipefail

target="${1:-}"
root="$(git worktree list --porcelain | sed -n '1s/^worktree //p')"
base="$root/.claude/worktrees"
here="$(git rev-parse --show-toplevel)"

if [ -z "$target" ]; then
  echo "ВНИМАНИЕ: не запускать без WT, пока работают агенты (чистый worktree работающего агента будет удалён)."
fi

# Список "путь<TAB>ветка" для worktree внутри $base
list_worktrees() {
  git worktree list --porcelain | awk -v base="$base/" '
    /^worktree / { p = substr($0, 10); b = "" }
    /^branch /   { b = substr($0, 8); sub("^refs/heads/", "", b) }
    /^$/         { if (index(p, base) == 1) print p "\t" b; p = "" }
    END          { if (p != "" && index(p, base) == 1) print p "\t" b }'
}

removed=0
skipped=0

clean_one() {
  local path="$1" branch="$2"
  if [ "$path" = "$here" ]; then
    echo "пропуск (текущий): $path"; skipped=$((skipped + 1)); return 0
  fi
  if [ -n "$(git -C "$path" status --porcelain)" ]; then
    echo "пропуск (есть незакоммиченные изменения): $path"; skipped=$((skipped + 1)); return 0
  fi
  if ! git worktree remove "$path"; then
    echo "пропуск (не удалось удалить): $path"; skipped=$((skipped + 1)); return 0
  fi
  echo "удалён: $path"
  removed=$((removed + 1))
  case "$branch" in
    worktree-agent-*)
      if [ -n "$(git for-each-ref --contains "refs/heads/$branch" --format='%(refname)' refs/heads/feature refs/heads/fix)" ]; then
        git branch -D "$branch" > /dev/null && echo "ветка удалена: $branch"
      else
        echo "ветка оставлена (не входит в feature/* или fix/*): $branch"
      fi ;;
    *) [ -z "$branch" ] || echo "ветка оставлена: $branch" ;;
  esac
}

entries="$(list_worktrees)"
if [ -n "$target" ]; then
  case "$target" in
    /*) want="$target" ;;
    */*) want="$(cd "$target" 2>/dev/null && pwd)" || want="$target" ;;
    *) want="$base/$target" ;;
  esac
  entries="$(printf '%s\n' "$entries" | awk -F'\t' -v w="$want" '$1 == w')"
  if [ -z "$entries" ]; then
    echo "worktree не найден в $base: $target"; exit 1
  fi
fi

if [ -z "$entries" ]; then
  echo "кандидатов нет"
else
  echo "кандидаты:"
  printf '%s\n' "$entries" | awk -F'\t' '{ print "  " $1 " [" $2 "]" }'
  while IFS=$'\t' read -r path branch; do
    clean_one "$path" "$branch"
  done <<< "$entries"
fi

git worktree prune
echo "готово: удалено $removed, пропущено $skipped"
