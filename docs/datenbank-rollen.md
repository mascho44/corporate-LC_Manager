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

Row-Level-Security ist nicht eingerichtet; die Mandantentrennung bleibt in Anwendung, Fremdschlüsseln und Triggern. 
## Lokale Sicherungen verschlüsseln und aufräumen (optional)

`scripts/backup-prod.sh` kennt zwei Umgebungsvariablen:

- `BACKUP_PASSPHRASE_FILE=/pfad/zur/passwortdatei`: Die Sicherung wird mit AES-256 verschlüsselt (`.dump.enc`), die Klartextdatei wird gelöscht. Die Passwortdatei gehört außerhalb des Backup-Ordners abgelegt (Rechte 600) und getrennt von der Sicherung aufbewahrt. **Ohne sie ist die Sicherung nicht wiederherstellbar.**
- `BACKUP_KEEP=14`: Es bleiben nur die neuesten N lokalen Sicherungen. Ohne die Variable wird nichts gelöscht.

Wiederherstellungstest einer verschlüsselten Sicherung: `BACKUP_PASSPHRASE_FILE=... bash scripts/test-backup-restore.sh /pfad/lcmanager-….dump.enc`.

Ohne die Variablen verhält sich alles wie bisher: unverschlüsselte Dumps mit Rechten 600; verschlüsselt wird dann erst die externe Sicherung mit restic.

## Mandant in der Datenbanksitzung

Bei jeder Verbindung aus dem Pool setzt die App unter PostgreSQL die Sitzungsvariable `app.tenant_id` auf den aktuellen Mandanten (`TenantSessionDataSource`). Sie ist die Vorbereitung für Row-Level-Security-Richtlinien. 
## Row-Level-Security (Pilot)

Seit den Migrationen V121 bis V123 sind `learned_field_label`, `lc_review_step`, `intake_channel`, `inbox_capture_info`, `document_check_decision`, `lc_note`, `lc_requirement_mapping`, `document_comparison` und `sanctions_clearance` per Row-Level-Security geschützt: Die Laufzeitrolle sieht und ändert nur Zeilen des Mandanten aus `app.tenant_id`; ohne Variable sieht sie nichts. Der Tabellenbesitzer (Flyway) ist ausgenommen. Die Lösch-Trigger der beiden Tabellen laufen als `SECURITY DEFINER`, damit das Löschen eines Mandanten auch aus einem anderen Mandantenkontext vollständig ist; die Mandanten-Inventur setzt den Mandanten für ihre Transaktion selbst. Die übrigen Mandanten-Tabellen folgen schrittweise.
