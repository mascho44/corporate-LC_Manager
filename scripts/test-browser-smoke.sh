#!/usr/bin/env bash
# Starts a disposable, synthetic localhost installation, opens the pages in headless Chrome and fails on uncaught JavaScript errors.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project="lc-browser-smoke-$$"
for dependency in docker node curl; do command -v "$dependency" >/dev/null || { echo "Missing test dependency: $dependency" >&2; exit 1; }; done
compose=(docker compose --project-name "$project" --file "$script_dir/docker-compose.tenant-test.yml")
cleanup(){ local result=$?; trap - EXIT INT TERM; [[ "$result" != 0 ]] && "${compose[@]}" logs --tail 60 app >&2 || true; "${compose[@]}" down --volumes || result=1; exit "$result"; }
trap cleanup EXIT
"${compose[@]}" up --detach --build
for attempt in {1..120}; do
  if curl --max-time 2 --fail --silent http://127.0.0.1:18086/api/health >/dev/null && "${compose[@]}" logs app | grep -q "Initial administrator 'admin' created"; then
    node "$script_dir/browser-smoke.cjs"; exit 0
  fi
  sleep 2
done
echo "Local test environment did not become ready in time." >&2; exit 1
