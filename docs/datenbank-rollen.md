# Datenbankrollen: Besitzer und Laufzeitrolle

Standardmäßig nutzt die App für Migrationen **und** Laufzeit dieselbe Rolle `lcmanager`. Diese Rolle ist in der Produktion Postgres-Superuser. Ein Fehler in der App könnte dadurch das Schema ändern oder Trigger abschalten.

Optional trennt die App beides:

| Rolle | Aufgabe | Rechte |
|---|---|---|
| `lcmanager` (Besitzer) | Flyway-Migrationen | alle, nur beim Start genutzt |
| `DB_APP_USER` | Laufzeit der App | nur SELECT, INSERT, UPDATE, DELETE und Sequenzen; kein DDL, kein TRUNCATE, kein Superuser |

## Einrichten (Server, einmalig)

1. In `.env` ergänzen: `DB_APP_USER=lcmanager_app` und `DB_APP_PASSWORD=<mindestens 24 zufällige Zeichen>`.
2. `bash scripts/db-app-role.sh` ausführen. Das Skript ist idempotent und legt die Rolle an; es vergibt die Rechte auch für künftige Tabellen, die Migrationen anlegen.
3. App neu starten (`scripts/deploy-prod.sh`). Ohne `DB_APP_USER` bleibt alles wie bisher.

Zurück zum alten Verhalten: `DB_APP_USER` und `DB_APP_PASSWORD` aus `.env` entfernen und neu starten.

## Grenzen

Row-Level-Security ist nicht eingerichtet; die Mandantentrennung bleibt in Anwendung, Fremdschlüsseln und Triggern. Lokale Sicherungsdateien sind unverschlüsselt (Rechte 600); verschlüsselt wird erst die externe Sicherung mit restic.
