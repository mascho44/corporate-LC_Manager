#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${RESTIC_REPOSITORY:?Externes Restic-Ziel muss konfiguriert sein}"
: "${RESTIC_PASSWORD_FILE:?Private Passwortdatei muss konfiguriert sein}"
if [[ ! -s "$RESTIC_PASSWORD_FILE" ]]; then
  echo "Restic-Passwortdatei fehlt oder ist leer." >&2
  exit 1
fi
command -v restic >/dev/null
export RESTIC_REPOSITORY RESTIC_PASSWORD_FILE
BACKUP_FILE="$(bash "$APP_DIR/scripts/backup-prod.sh")"
# Das vorhandene Repository muss vorher bewusst mit 'restic init' eingerichtet sein.
# Auch die Betriebskonfiguration sichern: enthält den TOTP-Schlüssel und wird verschlüsselt.
restic backup --tag corporate-lc-manager "$BACKUP_FILE" "$APP_DIR/.env"
restic check
echo "Verschlüsselte externe Sicherung und Repository-Prüfung erfolgreich."
