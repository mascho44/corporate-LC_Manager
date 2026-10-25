#!/usr/bin/env bash
# Starts and removes ONLY a uniquely named, synthetic localhost test installation.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project="lc-tenant-test-$$"
for dependency in docker node curl; do
  command -v "$dependency" >/dev/null || { echo "Missing test dependency: $dependency" >&2; exit 1; }
done
node -e 'if(Number(process.versions.node.split(".")[0])<20){console.error("Node.js 20 or newer is required.");process.exit(1)}'
docker compose version >/dev/null
if [[ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$project")" ]]; then
  echo "Test project already exists; refusing to reuse or remove it." >&2
  exit 1
fi
compose=(docker compose --project-name "$project" --file "$script_dir/docker-compose.tenant-test.yml")
cleanup(){
  local result=$?
  trap - EXIT INT TERM
  if [[ "$result" != 0 ]]; then
    "${compose[@]}" logs --tail 80 app >&2 || true
  fi
  if ! "${compose[@]}" down --volumes; then
    echo "Test cleanup failed; remaining project: $project" >&2
    result=1
  fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
echo "Starting disposable tenant acceptance environment: $project"
"${compose[@]}" up --detach --build
for attempt in {1..120}; do
  if curl --max-time 2 --fail --silent http://127.0.0.1:18086/api/health >/dev/null &&
      "${compose[@]}" logs app | grep -q "Initial administrator 'admin' created"; then
    node "$script_dir/test-tenant-workspaces.cjs"
    exit 0
  fi
  sleep 2
done
echo "Local test environment did not become ready in time." >&2
"${compose[@]}" logs --tail 15 app >&2
exit 1
