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
   Das Skript vergibt auch das Ausführungsrecht für die kontrollierten Mitgliedschaftsfunktionen (V64/V65). Nach Updates, die solche Funktionen hinzufügen, das Skript erneut ausführen.
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

Seit den Migrationen V121 bis V129 sind `learned_field_label`, `lc_review_step`, `intake_channel`, `inbox_capture_info`, `document_check_decision`, `lc_note`, `lc_requirement_mapping`, `document_comparison`, `sanctions_clearance`, `customer`, `inbox_customer_assignment`, die drei `procurement_*`-Tabellen, `charge_profile`, `charge_estimate`, `document_template`, `company_profile`, `letter_of_credit`, `lc_document`, `lc_amendment`, `lc_task` sowie die Kindtabellen `lc_condition`, `lc_additional_field` und `lc_required_document` (diese folgen ihrer Akte), `document_inbox_item`, `document_draft` und die Trainingstabellen (`document_metadata_training`, `document_spatial_layout`, `document_split_training`, `document_type_example`, `training_session`, `training_learning_control`) sowie `workflow`, `team` (mit `team_member`), `automation_rule`, `document_approval_threshold`, `tenant_scan_profile` `tenant_ai_setting`, `ebics_connection`, `ebics_message`, `swift_import_record`, `swift_message`, `integration_outbox`, `email_delivery`, `internal_rule_pack`, `internal_rule_pack_version` und `capture_token` per Row-Level-Security geschützt: Die Laufzeitrolle sieht und ändert nur Zeilen des Mandanten aus `app.tenant_id`; ohne Variable sieht sie nichts. Der Tabellenbesitzer (Flyway) ist ausgenommen. Wechselt der Code innerhalb einer Transaktion den Mandanten (`TenantContext.open`), folgt die Datenbanksitzung diesem Wechsel. Die Mandanten-Abnahme der CI läuft mit der eingeschränkten Rolle und prüft so Anlage, Isolation und Löschung unter RLS. Die Lösch-Trigger der beiden Tabellen laufen als `SECURITY DEFINER`, damit das Löschen eines Mandanten auch aus einem anderen Mandantenkontext vollständig ist; die Mandanten-Inventur setzt den Mandanten für ihre Transaktion selbst. `capture_token` ist ebenfalls geschützt; die Token-Suche vor der Mandantenbestimmung läuft im ausdrücklichen Plattform-Scope (`TenantContext.openPlatform()`, Sitzungsvariable `app.platform`). Bewusst ohne RLS bleiben die globale Identität (`app_user`, `tenant_membership*`, `tenant`, `platform_invitation`, `password_reset_token`), `app_role`/`app_role_permission` und `audit_event`: Sie werden an vielen Stellen mandantenübergreifend gelesen oder für fremde Mandanten geschrieben (Einladungen, Plattform-Konten, Plattformübersicht, Audit-Kette); der Schutzgewinn stünde in keinem Verhältnis zum Umbau.

## Heimatmandant

Der Heimatmandant hält die globalen Benutzerkonten und die Plattform-Anmeldung. Er steht in `platform_home_tenant` (genau ein Datensatz, Migration V130) und ist standardmäßig der Mandant `default`. Die App liest ihn beim Start (`HomeTenantLoader`) und nutzt ihn überall, wo bisher `default` fest verdrahtet war (`Tenant.homeId()`). Das ist die Grundlage dafür, den alten Standardmandanten später entfernen zu können.

### Heimatmandanten wechseln

In der Plattform-Verwaltung bei einem aktiven Mandanten: „Als Heimatmandant festlegen“ (Code eintippen, Umzug bestätigen). Technisch dahinter:

`PUT /api/platform/home-tenant` mit `{"tenantCode":"…","acknowledgeAccountMove":true}` (nur Plattform-Administratoren mit Zwei-Faktor) verlegt den Heimatmandanten. Alle Konten des bisherigen Heimatmandanten ziehen mit und behalten ihre Rolle nach Namen, oder die Standardrolle gleicher Art (Administrator, Editor, Viewer), wenn der Name im Zielmandanten fehlt; der Zielmandant muss aktiv, nicht archiviert und nicht gelöscht sein und alle diese Rollen besitzen. Der Wechsel läuft in einer Transaktion und bricht ab, wenn dabei Plattform-Administratoren verloren gingen; er ist im Audit vermerkt und lässt sich durch einen Wechsel zurück rückgängig machen. Bestehende Mitgliedschaften der Konten in anderen Mandanten bleiben unberührt.

### Bestehendes Konto als erster Mandantenadmin

Beim Anlegen eines Mandanten genügt als „erster Mandantenadmin“ der Benutzername eines bestehenden Kontos: Es erhält sofort die Rolle „Administrator“ des neuen Mandanten, Name und E-Mail entfallen, und es wird nichts eingeladen. Ohne bestehendes Konto sind Name und E-Mail nötig, dann wird ein neues Konto eingeladen. Ein Plattformadministrator wird bei getrennter Plattform-Verwaltung abgelehnt; das Konto muss aktiv sein und darf keine offene Einladung haben. Das Audit vermerkt den Fall.
