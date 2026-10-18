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

# Optional: lokale Sicherung verschluesseln (AES-256, Passwortdatei ausserhalb des Backup-Ordners).
if [[ -n "${BACKUP_PASSPHRASE_FILE:-}" ]]; then
  if [[ ! -s "$BACKUP_PASSPHRASE_FILE" ]]; then
    echo "Fehler: BACKUP_PASSPHRASE_FILE fehlt oder ist leer." >&2
    exit 1
  fi
  openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass "file:$BACKUP_PASSPHRASE_FILE" \
    -in "$BACKUP_FILE" -out "$BACKUP_FILE.enc"
  chmod 600 "$BACKUP_FILE.enc"
  rm -f -- "$BACKUP_FILE"
  BACKUP_FILE="$BACKUP_FILE.enc"
fi

# Optional: nur die neuesten N lokalen Sicherungen behalten (leer = alles behalten).
if [[ "${BACKUP_KEEP:-}" =~ ^[1-9][0-9]*$ ]]; then
  ls -1t "$BACKUP_DIR"/lcmanager-*.dump "$BACKUP_DIR"/lcmanager-*.dump.enc 2>/dev/null \
    | tail -n +"$((BACKUP_KEEP + 1))" | while read -r old; do rm -f -- "$old"; done
fi
echo "$BACKUP_FILE"
