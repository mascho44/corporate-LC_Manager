# Eingangskanäle: gemeinsamer Eingang und Ordner-Kanal (Scan to Folder)

Stand: 17.10.2026. Alles, was nicht von Hand hochgeladen wird, geht durch **einen** Weg in den Posteingang. Darauf setzen die Kanäle auf: die Scan-Station
(REST, [Erfassungszugang](erfassungszugang.md)), der Ordner-Kanal (diese Seite) und später ein Postfach (E-Mail, noch nicht gebaut).

## Gemeinsamer Eingang (`InboxIntakeService`)
Ein Kanal liefert `IntakeItem`s (Dateiname, Inhalt, Typ) mit ihrer **Herkunft** (`IntakeOrigin`): Kanal (`SCAN_STATION`, `EMAIL`, `FOLDER`), Referenz (Message-ID,
Dateipfad), Stapel, Gerät, Absender, Betreff, gemeldete Seitenzahl, Zeitpunkt, angegebene Prüfsumme. Zurück kommt je Datei eine Quittung (`IntakeReceipt`).

* **Doppelte:** Dieselbe Datei (gleicher SHA-256 im Mandanten) wird nicht noch einmal angelegt, auch nicht über einen anderen Kanal. Die Quittung (`DUPLICATE`) nennt den früheren Eintrag; ein Kanal darf also gefahrlos wiederholen.
* **Prüfsumme und Seitenzahl:** Eine angegebene Prüfsumme muss stimmen (sonst wird abgelehnt); weicht die gemeldete Seitenzahl von der erkannten ab, steht das in der Quittung und am Dokument.
* **Herkunft:** Kanal, Referenz, Absender und Betreff stehen am Posteingangs-Eintrag (Tabelle `inbox_capture_info`, Spalten `channel`, `origin_reference`, `sender`, `subject`).
* Die Grenzen des normalen Uploads gelten (10 MB je Datei, keine ZIP-Archive über die Kanäle).

## Kanal-Schnittstelle (`IntakeChannel`)
Ein Kanal, der selbst abruft, liefert über `poll(Konfiguration, Sink)` Dateien an den Eingang und handelt **erst nach der Quittung** (Datei verschieben, Mail als gelesen markieren). Eine nicht
zustellbare Datei geht so nie verloren. Ein neuer Kanal ist eine Klasse mit `type()` und `poll()`; Konfiguration, Zeitplan, Verwaltung und Protokoll sind schon da.

## Ordner-Kanal („Scan to Folder")
Der Scanner (oder seine Software) legt Dateien im Ordner `eingang` ab. Alle paar Minuten liest die Anwendung die fertigen Dateien:

| Ordner | Inhalt |
|---|---|
| `eingang` | Hier legt der Scanner ab. |
| `verarbeitet` | Gelieferte Dateien, mit Zeitstempel im Namen (auch Duplikate). |
| `fehler` | Abgelehnte Dateien mit Zeitstempel und einer Datei `….fehler.txt` mit dem Grund. |

* **Pfad:** `<Wurzel>/<Mandanten-ID>/<Kanalname>/eingang` (Wurzel `app.intake.folder-root`, Standard `/data/intake`). Der Administrator gibt nie einen Pfad ein; der Kanalname darf nur Kleinbuchstaben, Ziffern und Bindestrich enthalten (höchstens 40 Zeichen). Symbolische Links werden ignoriert.
* **Dateien:** PDF, PNG, JPG, TIF bis 10 MB. Andere Typen, leere und zu große Dateien kommen nach `fehler`. Versteckte (`.x`) und unfertige Dateien (`.tmp`, `.part`, `.crdownload`, `~`) bleiben liegen.
* **Ruhezeit:** Eine Datei wird erst gelesen, wenn sie 10 Sekunden unverändert ist (`app.intake.settle-seconds`), damit eine noch schreibende Scannersoftware nicht stört. Höchstens 50 Dateien je Lauf.
* **Zeitplan:** Ein Hintergrundjob prüft alle 30 Sekunden (`app.intake.poll-ms`), je Mandant, welche eingeschalteten Kanäle fällig sind (Intervall 1 bis 1440 Minuten, Standard 2). Läufe überlappen nicht.
* **Durchsuchbare PDFs:** Scanner mit eigener Texterkennung liefern PDFs mit Textebene. Diese Ebene wird direkt genutzt, dafür läuft keine OCR. Reine Bild-PDFs gehen durch die normale OCR des Posteingangs.
* **Absender im Posteingang:** „folder:<Kanalname>".

### Einrichten
1. Mandanteneinstellungen → *Daten & Zugänge* → *Eingangskanäle* (Recht „Einstellungen verwalten"): Namen und Intervall eintragen, *Ordner-Kanal anlegen*. Der Kanal ist danach eingeschaltet und zeigt den Pfad des Eingangsordners.
2. **Server:** Das Verzeichnis `/data/intake` des Containers muss von außen erreichbar sein, damit der Scanner dort schreiben kann: ein Volume oder Bind-Mount, z. B. mit einer SMB- oder SFTP-Freigabe des Servers darauf. Das Image legt `/data/intake` für den Anwendungsbenutzer an; die Einbindung selbst ist Betreiber-Konfiguration (nicht Teil des Repositorys). Der Container muss dort schreiben dürfen (Benutzer `lcmanager`).
3. Den Scanner auf die Freigabe `…/<Mandanten-ID>/<Kanalname>/eingang` ablegen lassen. *Jetzt prüfen* löst einen Lauf sofort aus; das Ergebnis steht in der Zeile („1 neu, 0 bereits vorhanden, 0 fehlgeschlagen" oder „Fehler: …", rot).

### Verwaltung, Rechte, Protokoll
REST `GET/POST /api/intake/channels`, `PUT/DELETE /api/intake/channels/{id}`, `POST …/{id}/run`, alle mit dem Recht „Einstellungen verwalten". Anlegen, Ändern, Löschen und manuelle Läufe stehen im Audit-Protokoll
(`INTAKE_CHANNEL_CREATED/UPDATED/DELETED/RUN`). Die Kanäle gehören dem Mandanten und werden mit ihm gelöscht (Tabelle `intake_channel`, Migration V118).

## Noch offen
* **E-Mail-Kanal (IMAP, Issue #18):** ein zentrales Postfach je Mandant, freigegebene Absender, Anhänge in den Posteingang, Mail danach verschieben oder markieren, Kennwort verschlüsselt gespeichert und nur schreibbar. Entwurf abgestimmt, noch nicht gebaut.
* Anzeige der Herkunft (Absender, Betreff) in der Posteingangs-Oberfläche; Benachrichtigung bei Kanälen, die lange nichts liefern.
* Optionale Begleitdateien eines Scanners (OCR-Text neben dem Bild) werden nicht ausgewertet; durchsuchbare PDFs reichen.
