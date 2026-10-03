#!/usr/bin/env bash
# Порт сервиса из docs/ports.md (единственный источник). Подключается через source, нужен $ROOT.
# Использование: PORT=$(svc_port workspace-service)
svc_port() {
  local p
  p=$(sed -nE "s/^- $1 ([0-9]+)\$/\1/p" "$ROOT/docs/ports.md" | head -n 1)
  [ -n "$p" ] || { echo "FAIL нет порта сервиса $1 в docs/ports.md" >&2; return 1; }
  echo "$p"
}
