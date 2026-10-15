# Erfassungszugang für Scan-Stationen

Eine Scan-Station (z. B. in der Poststelle) liefert Dokumente über einen **Erfassungszugang** in den Posteingang eines Mandanten, ohne Benutzerkonto und Sitzung.

* **Anlegen:** Mandanteneinstellungen → *Erfassungszugänge* (Recht „Einstellungen verwalten"). Der Zugang bekommt einen Namen (z. B. „Poststelle Scanner 2"); das
  Geheimnis (`lcc_…`) wird **einmal** angezeigt. Gespeichert wird nur sein SHA-256-Hash. Höchstens 20 aktive Zugänge je Mandant, Widerruf jederzeit.
* **Was er darf:** ausschließlich die Dokumentenlieferung: `POST /api/capture/upload` (empfohlen, mit Metadaten) und `POST /api/inbox` (Datei-Upload in den Posteingang des eigenen Mandanten). Jeder andere Pfad antwortet 403. Der Zugang
  hat kein Recht außer „Dokumente hochladen", benötigt keinen CSRF-Token und erzeugt keine Sitzung. Er funktioniert nur, solange der Mandant aktiv ist.
* **Aufruf:**
  ```
  curl -X POST https://<server>/api/inbox -H "Authorization: Bearer lcc_…" -F "file=@scan.pdf"
  ```
  Die Antwort (`202`) enthält die Posteingangs-Einträge; der Eingang erscheint im Posteingang als „capture:<Name>".
* **Protokoll:** Anlegen und Widerruf stehen im Audit-Protokoll; der Upload wie jeder Posteingang. Abgelehnte Aufrufe erscheinen im Protokoll abgelehnter Anfragen.
## Lieferung mit Metadaten: `POST /api/capture/upload`
Multipart-Felder: `file` (eine Datei je Aufruf; keine ZIP-Archive), optional `batchId` (Stapel), `scanner` (Gerät), `pages` (gemeldete Seitenzahl), `scannedAt` (ISO-8601, z. B. `2026-10-13T08:15:00+02:00`), `sha256` (Prüfwert der Datei).
```
curl -X POST https://<server>/api/capture/upload -H "Authorization: Bearer lcc_…" \
  -F "file=@scan.pdf" -F "batchId=17" -F "scanner=Scanner 2" -F "pages=4" -F "sha256=$(shasum -a 256 scan.pdf | cut -d' ' -f1)"
```
Die Antwort (`202`) ist eine Quittung je Datei: `status` `CREATED` oder `DUPLICATE`, `inboxItemId`, `sha256`, gemeldete und erkannte Seiten, `pageCountMatches`, `note`.
* **Doppelte Lieferung:** Dieselbe Datei (gleicher SHA-256) wird nicht ein zweites Mal angelegt; die Quittung nennt den früheren Eintrag. Ein Scanner darf also gefahrlos wiederholen.
* **Prüfwert:** Stimmt `sha256` nicht mit der Datei überein, wird die Lieferung abgelehnt (Übertragungsfehler).
* **Seitenzahl:** Weicht `pages` von der erkannten Seitenzahl der PDF ab, steht das in der Quittung und als Warnung am Dokument im Posteingang („Gemeldet 4, erkannt 3 – bitte den Stapel prüfen").
* **Anzeige:** Im Posteingang steht am Dokument eine Zeile „Eingang: Poststelle Scanner 2 · Gerät … · Stapel … · … Seiten gemeldet · gescannt …".

## Noch offen
Die Kundenzuordnung steuert noch nicht die Auswahl der LC-Akte; Benachrichtigung, wenn lange nichts von einem Zugang kommt.

## Stille Zugänge
Ein aktiver Zugang, der seit mindestens zwei Arbeitstagen (Montag bis Freitag, Wochenenden zählen nicht, Feiertage sind nicht bekannt) nichts geliefert hat, gilt als „ruhig". Als Ausgangspunkt dient die letzte Lieferung, bei noch nie benutzten Zugängen das Anlegedatum. In den Einstellungen steht dann „⚠ ruhig seit N Arbeitstagen"; im Posteingang erscheint für alle, die ihn sehen dürfen, ein Hinweis, damit jemand in der Poststelle nachfragt. Widerrufene Zugänge werden nicht gemeldet. Eine E-Mail dazu gibt es noch nicht.

## Poststellen-Seite (`/poststelle.html`)
Für die Poststelle gibt es eine einfache Seite ohne Anmeldung: `https://<server>/poststelle.html`. Dort trägt man einmal den Erfassungszugang und den Gerätenamen ein (optional „Auf diesem Gerät merken", sonst nur für diesen Besuch), gibt den Stapel an (Vorgabe: heutiges Datum) und zieht Dateien in das Feld oder wählt sie aus. Jede Datei wird einzeln mit Prüfwert an `POST /api/capture/upload` gesendet; die Seite zeigt je Datei „angeliefert", „schon früher angeliefert" oder einen Fehler in Klartext (z. B. Zugang widerrufen). Die Seite selbst enthält keine Geheimnisse; ohne gültigen Zugang kann man nichts anliefern. Sie ist handytauglich.

## E-Mail bei stillen Zugängen
Ab 7 Uhr prüft der stündliche Hintergrundjob je Mandant die Erfassungszugänge. Ist mindestens einer still (wie in der Anzeige „still"), bekommen die aktiven Administratoren des Mandanten **höchstens einmal täglich** eine E-Mail mit den betroffenen Zugängen (Name, seit wie vielen Arbeitstagen keine Lieferung bzw. „noch nie genutzt"). Voraussetzung: E-Mail-Versand eingerichtet (`app.mail.enabled`, `app.mail.from`).
