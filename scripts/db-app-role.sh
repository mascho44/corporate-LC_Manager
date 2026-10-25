#!/usr/bin/env bash
# Legt die eingeschraenkte Laufzeitrolle fuer die App an (idempotent). Siehe docs/datenbank-rollen.md.
# Aufruf auf dem Server: bash scripts/db-app-role.sh   (liest DB_APP_USER und DB_APP_PASSWORD aus .env)
set -Eeuo pipefail
umask 077
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$APP_DIR/docker-compose.prod.yml"
ENV_FILE="$APP_DIR/.env"
[[ -f "$ENV_FILE" ]] || { echo "Fehler: $ENV_FILE fehlt." >&2; exit 1; }
get() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'; }
APP_USER="$(get DB_APP_USER)"
APP_PASSWORD="$(get DB_APP_PASSWORD)"
if [[ ! "$APP_USER" =~ ^[a-z_][a-z0-9_]{2,40}$ ]]; then echo "DB_APP_USER fehlt oder ist ungueltig (a-z, 0-9, _)." >&2; exit 1; fi
if [[ ${#APP_PASSWORD} -lt 24 ]]; then echo "DB_APP_PASSWORD fehlt oder ist kuerzer als 24 Zeichen." >&2; exit 1; fi
# Passwort und Rollenname gehen per psql-Variable ueber stdin, nie in die Prozessliste.
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  psql -U lcmanager -d lcmanager -v ON_ERROR_STOP=1 -v app_user="$APP_USER" -v app_pw="$APP_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', :'app_user', :'app_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'app_user', :'app_pw') \gexec
SELECT format('GRANT CONNECT ON DATABASE lcmanager TO %I', :'app_user') \gexec
SELECT format('REVOKE CREATE ON SCHEMA public FROM %I', :'app_user') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE lcmanager IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE lcmanager IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I', :'app_user') \gexec
-- V64/V65 entziehen PUBLIC das Ausfuehrungsrecht der kontrollierten Mitgliedschaftsfunktionen; die App-Rolle braucht es ausdruecklich.
SELECT format('GRANT EXECUTE ON FUNCTION provision_tenant_membership(uuid,uuid,uuid), update_tenant_membership_role(uuid,uuid,uuid), remove_tenant_membership(uuid,uuid) TO %I', :'app_user') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE lcmanager IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO %I', :'app_user') \gexec
SQL
echo "Rolle $APP_USER eingerichtet. Danach DB_APP_USER/DB_APP_PASSWORD in .env belassen und die App neu starten."
