#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$APP_DIR/docker-compose.prod.yml"
ENV_FILE="$APP_DIR/.env"
BACKUP_DIR="${BACKUP_DIR:-$APP_DIR/backups}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="$BACKUP_DIR/lcmanager-$TIMESTAMP.dump"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Fehler: $ENV_FILE fehlt." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
PARTIAL_FILE="$(mktemp "$BACKUP_DIR/.lcmanager-$TIMESTAMP.XXXXXX")"
trap 'rm -f -- "$PARTIAL_FILE"' EXIT

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  pg_dump -U lcmanager -d lcmanager --format=custom > "$PARTIAL_FILE"

if [[ ! -s "$PARTIAL_FILE" ]]; then
  echo "Fehler: Die Datenbanksicherung ist leer." >&2
  exit 1
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  pg_restore --list < "$PARTIAL_FILE" > /dev/null
mv -n -- "$PARTIAL_FILE" "$BACKUP_FILE"
if [[ -f "$PARTIAL_FILE" ]]; then
  echo "Fehler: Sicherung mit gleichem Zeitstempel vorhanden." >&2
  exit 1
fi

chmod 600 "$BACKUP_FILE"
echo "$BACKUP_FILE"
