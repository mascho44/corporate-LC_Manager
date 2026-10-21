# Upload-, Verarbeitungs- und Backup-Sicherheit

## Upload und PDF/OCR

ZIP-Import in Posteingang und LC-Akte verwenden dieselbe Vorprüfung:
maximal 100 Dateien, 1.000 Archiv-Einträge, 10 MB pro Datei und 50 MB
entpackter Inhalt; keine verschachtelten ZIPs, unsicheren Pfade oder leeren Dateien.
Die Vorprüfung erfolgt vor Extraktion oder Speicherung. Unbekannte Anhänge
bleiben zulässig; eine allgemeine Dateityp-Allowlist wird noch nicht erzwungen.

PDF-Verarbeitung ist auf zwei gleichzeitige Vorgänge pro Instanz begrenzt.
PDFs mit mehr als 200 Seiten oder zwölf Millionen Pixeln pro Seite bei 200 DPI
werden nicht verarbeitet. OCR/Ausschnitte berücksichtigen maximal 20 Seiten.
Native Prozesse haben Zeitlimits und werden bei Überschreitung beendet.
Ungelesene Ausgabe-Pipes können das Zeitlimit nicht mehr blockieren.
OCR-Ausgaben werden vor dem Einlesen auf 10 MB begrenzt. Dies ist keine
vollständige Sandbox für Java-PDF-Verarbeitung.

Der Produktionscontainer erhält ein schreibgeschütztes Dateisystem,
`no-new-privileges`, keine Linux-Capabilities, maximal zwei CPUs, 2 GB RAM,
256 Prozesse und ein temporäres 512-MB-Dateisystem. Der JVM-Heap verwendet
maximal 60 Prozent des Containerlimits. Das PostgreSQL-Volume bleibt unverändert.

## Optionaler interner Virenscan

ClamAV INSTREAM prüft Multipart-Dateien vor den Controllern, auch Avatare,
Dokumentmuster und Trainingsuploads. Zwei parallele Scans und 20 Sekunden
Gesamtlaufzeit begrenzen die Belastung. Aktivierter Scan schlägt geschlossen
fehl: Schadsoftware, Scannerfehler oder Nichterreichbarkeit verhindern Annahme.

**Standardmäßig deaktiviert:** Zuerst einen vertrauenswürdigen ClamAV-Daemon
mit aktuellen Signaturen im internen Netzwerk bereitstellen:

```text
UPLOAD_ANTIVIRUS_ENABLED=true
UPLOAD_ANTIVIRUS_HOST=clamav
UPLOAD_ANTIVIRUS_PORT=3310
```

Die Compose-Datei enthält dafür das Profil `antivirus` (Dienst `clamav`, nur im
internen Netzwerk, Signaturen im Volume `lc-clamav`, Start dauert einige Minuten):

```bash
docker compose -f docker-compose.prod.yml --profile antivirus up -d
```

Ohne das Profil startet kein Scanner; der Standard bleibt aus. Erst danach
`UPLOAD_ANTIVIRUS_ENABLED=true` setzen, sonst werden Uploads bei aktivem Scan abgelehnt.

Keinen Scanner-Port öffentlich veröffentlichen. ClamAV muss mindestens 50 MB
INSTREAM unterstützen. Archiv-/Dateigrößenlimits und Warnungen bei überschrittenen
Scanlimits müssen passend eingerichtet und getestet werden. Es gibt keinen
externen Analyseportal-Upload. Die Anbindung installiert noch keinen Scanner
und bestätigt keine aktuellen Virensignaturen.

## Sicherungen

`backup-prod.sh` erstellt Dumps zunächst privat unter temporärem Dateinamen,
prüft das PostgreSQL-Archiv und veröffentlicht erst dann die fertige Sicherung.
Fehler hinterlassen keinen vermeintlich gültigen Dump. Lokale Dumps bleiben
unverschlüsselt, mit Dateirechten 600.

### Tägliche lokale Sicherung (eingerichtet)

Auf dem Produktivserver läuft täglich um 03:30 Uhr (Cron des Benutzers `administrator`):

```
30 3 * * * PATH=/usr/local/bin:/usr/bin:/bin BACKUP_KEEP=30 /home/administrator/corporate-lc-manager/scripts/backup-prod.sh >> /home/administrator/lcm-backup.log 2>&1
```

Die Sicherung ist verschlüsselt (`.dump.enc`), weil die Passwortdatei `~/.lcm-backup-passphrase` vorhanden ist (siehe `datenbank-rollen.md`). `BACKUP_KEEP=30` behält die 30 neuesten Sicherungen und löscht die älteren; ohne die Variable wird nichts gelöscht. Auch jeder Deploy erzeugt eine Sicherung. Die Wiederherstellung wird mit `scripts/test-backup-restore.sh` geprüft.

**Grenze:** Diese Sicherungen liegen auf demselben Server wie die Datenbank. Ohne Offsite-Ziel (`backup-offsite.sh`, siehe unten) schützen sie nicht vor Verlust des Servers oder der Platte. Das Offsite-Ziel ist noch nicht eingerichtet.

`backup-offsite.sh` erstellt einen frischen Dump und sichert diesen sowie die
private Betriebskonfiguration mittels Restic verschlüsselt:

```bash
export RESTIC_REPOSITORY='s3:https://storage.example.com/lc-backups'
export RESTIC_PASSWORD_FILE='/privater/pfad/restic-password'
bash scripts/backup-offsite.sh
```

Restic muss installiert, das Ziel bewusst initialisiert und dessen Zugänge
privat konfiguriert sein. Passwortdatei mit Rechten 600 sichern und getrennt
aufbewahren. Die `.env` enthält unter anderem den TOTP-Schlüssel: Ein Restore
ohne diesen Schlüssel ist unvollständig. Ziel und Zeitplan werden nicht
automatisch eingerichtet. Das Skript löscht keine alten Sicherungen.

`restic check` prüft Repository-Struktur und Integrität, ersetzt aber keinen
vollständigen Restore. Periodisch zusätzlich `restic check --read-data` und
die Wiederherstellung eines heruntergeladenen Dumps prüfen:

```bash
bash scripts/test-backup-restore.sh /absoluter/pfad/sicherung.dump
```

Der Test erzeugt eine separate PostgreSQL-17-Datenbank ohne Netzwerk,
Host-Port oder Produktionsvolume, spielt das Backup ein und prüft die
Migrationstabelle. Nur der Testcontainer wird anschließend entfernt.
Das temporäre Dateisystem ist auf 512 MB begrenzt; größere Datenbanken
benötigen eine bewusst angepasste isolierte Testumgebung.

Ein externes Backup benötigt noch Speicherziel, Zugänge, Restic-Passwort und
Zeitplan. Ein externer Restore darf erst nach tatsächlicher Prüfung behauptet werden.

Grundlagen: [OWASP Uploads](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html),
[ClamAV](https://docs.clamav.net/manual/Usage/ClamdProtocol.html),
[Restic](https://restic.readthedocs.io/en/stable/).


## Abgelehnte Anfragen (401/403)

Jede abgelehnte Anfrage wird im Anwendungsprotokoll (`access-denied`, Stufe WARN) vermerkt: Status, Methode, Adresse **ohne Parameter**,
Benutzer, gewählter Mandant, Absenderadresse und ein Grund (z. B. „keine aktive Mitgliedschaft im gewählten Mandanten“,
„CSRF-Token fehlt“, „Berechtigung fehlt oder CSRF-Token ungültig“, „Sitzung abgelaufen, Zugangsdaten oder Rechte geändert“).
Inhalte, Parameter und Passwörter werden nie aufgenommen; Steuerzeichen werden entfernt (kein Einschleusen von Zeilen). Gleiche
Ablehnungen (gleicher Benutzer, gleiche Anfrageart, gleiche Adresse mit ersetzten IDs) erscheinen höchstens einmal pro Minute mit
der Zahl der unterdrückten Wiederholungen. Falsche Passwörter beim Login stehen weiterhin nur im Audit.

Die letzten 200 Einträge sehen Plattformadministratoren in der Plattformverwaltung unter „Abgelehnte Anfragen“
(`GET /api/platform/denials`); die Liste liegt im Arbeitsspeicher und wird beim Neustart geleert.
