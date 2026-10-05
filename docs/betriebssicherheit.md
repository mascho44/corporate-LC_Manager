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
