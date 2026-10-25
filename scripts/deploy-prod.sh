#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$APP_DIR/docker-compose.prod.yml"
ENV_FILE="$APP_DIR/.env"

cd "$APP_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Fehler: $ENV_FILE fehlt." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "Fehler: Auf dem Server liegen nicht übertragene Änderungen." >&2
  exit 1
fi

echo "Erstelle Datenbanksicherung ..."
BACKUP_FILE="$($APP_DIR/scripts/backup-prod.sh)"
echo "Sicherung erstellt: $BACKUP_FILE"

echo "Lade den freigegebenen Stand ..."
git fetch origin main
git merge --ff-only origin/main

echo "Baue und starte die Anwendung ..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build --remove-orphans

echo "Warte auf den Gesundheitsstatus ..."
for attempt in {1..30}; do
  STATUS="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' corporate-lc-manager-app-1 2>/dev/null || true)"
  if [[ "$STATUS" == "healthy" ]]; then
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
    echo "Deployment erfolgreich."
    exit 0
  fi
  sleep 2
done

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=100 app
echo "Fehler: Die Anwendung wurde nicht rechtzeitig gesund." >&2
exit 1
