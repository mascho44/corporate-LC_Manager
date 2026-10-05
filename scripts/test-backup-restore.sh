#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
if [[ $# -ne 1 || ! -s "$1" ]]; then
  echo "Aufruf: bash scripts/test-backup-restore.sh /absoluter/pfad/sicherung.dump" >&2
  exit 1
fi
BACKUP_FILE="$1"
TEST_NAME="lc-restore-test-$(date -u +%Y%m%dT%H%M%SZ)-$$"
cleanup() { docker rm -f "$TEST_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# Kein Port, kein Produktionsvolume, kein Netzwerk; wegwerfbare Testdatenbank.
docker run -d --name "$TEST_NAME" --network none --memory 512m --cpus 1 \
  --tmpfs /var/lib/postgresql/data:rw,size=512m \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=restorecheck postgres:17-alpine >/dev/null
READY=false
for attempt in {1..30}; do
  START_LOG="$(docker logs "$TEST_NAME" 2>&1)"
  if [[ "$START_LOG" == *"PostgreSQL init process complete"* ]] && docker exec "$TEST_NAME" pg_isready -U postgres -d restorecheck >/dev/null 2>&1; then READY=true; break; fi
  sleep 1
done
if [[ "$READY" != true ]]; then echo "Testdatenbank startet nicht." >&2; exit 1; fi
docker exec -i "$TEST_NAME" pg_restore --exit-on-error --no-owner --no-privileges \
  -U postgres -d restorecheck < "$BACKUP_FILE"
docker exec "$TEST_NAME" psql -U postgres -d restorecheck -v ON_ERROR_STOP=1 \
  -c 'SELECT count(*) AS migrations FROM flyway_schema_history WHERE success;'
echo "Wiederherstellung in isolierter Testdatenbank erfolgreich."
