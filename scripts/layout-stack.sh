#!/usr/bin/env bash
# Disposable, synthetic localhost installation with sample data for looking at the screens (and for scripts/layout-rules.js).
# Usage: scripts/layout-stack.sh up | down | code     (never point this at production)
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project="lc-layout-check"
compose=(docker compose --project-name "$project" --file "$script_dir/docker-compose.tenant-test.yml")
state="$script_dir/../target/layout-stack.json"
case "${1:-}" in
  up)
    for dependency in docker node curl; do command -v "$dependency" >/dev/null || { echo "Missing dependency: $dependency" >&2; exit 1; }; done
    mkdir -p "$(dirname "$state")"
    "${compose[@]}" up --detach --build
    for attempt in {1..150}; do
      if curl --max-time 2 --fail --silent http://127.0.0.1:18086/api/health >/dev/null &&
          "${compose[@]}" logs app | grep -q "Initial administrator 'admin' created"; then
        LAYOUT_STATE="$state" node "$script_dir/layout-seed.cjs"
        exit 0
      fi
      sleep 2
    done
    echo "The layout installation did not become ready in time." >&2
    "${compose[@]}" logs --tail 20 app >&2
    exit 1 ;;
  down) "${compose[@]}" down --volumes; rm -f "$state" ;;
  code) LAYOUT_STATE="$state" node "$script_dir/layout-seed.cjs" code ;;
  *) echo "Usage: $0 up|down|code" >&2; exit 2 ;;
esac
