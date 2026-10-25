# Eingangskanäle: gemeinsamer Eingang, Ordner-Kanal und E-Mail-Postfach

Stand: 17.10.2026. Alles, was nicht von Hand hochgeladen wird, geht durch **einen** Weg in den Posteingang. Darauf setzen die Kanäle auf: die Scan-Station
(REST, [Erfassungszugang](erfassungszugang.md)), der Ordner-Kanal und das E-Mail-Postfach (beide auf dieser Seite).

## Gemeinsamer Eingang (`InboxIntakeService`)
Ein Kanal liefert `IntakeItem`s (Dateiname, Inhalt, Typ) mit ihrer **Herkunft** (`IntakeOrigin`): Kanal (`SCAN_STATION`, `EMAIL`, `FOLDER`), Referenz (Message-ID,
Dateipfad), Stapel, Gerät, Absender, Betreff, gemeldete Seitenzahl, Zeitpunkt, angegebene Prüfsumme. Zurück kommt je Datei eine Quittung (`IntakeReceipt`).

* **Doppelte:** Dieselbe Datei (gleicher SHA-256 im Mandanten) wird nicht noch einmal angelegt, auch nicht über einen anderen Kanal. Die Quittung (`DUPLICATE`) nennt den früheren Eintrag; ein Kanal darf also gefahrlos wiederholen.
* **Prüfsumme und Seitenzahl:** Eine angegebene Prüfsumme muss stimmen (sonst wird abgelehnt); weicht die gemeldete Seitenzahl von der erkannten ab, steht das in der Quittung und am Dokument.
* **Herkunft:** Kanal, Referenz, Absender und Betreff stehen am Posteingangs-Eintrag (Tabelle `inbox_capture_info`, Spalten `channel`, `origin_reference`, `sender`, `subject`).
* Die Grenzen des normalen Uploads gelten (10 MB je Datei, keine ZIP-Archive über die Kanäle).

## Anzeige im Posteingang
Am Dokument steht eine Zeile „Eingang: …": bei E-Mail „E-Mail von <Absender> · Betreff „…" · gesendet … · Postfach …", beim Ordner „Ordner <Kanal> · Datei … · abgelegt …", bei der Scan-Station wie bisher Gerät, Stapel und Seiten (`GET /api/inbox-capture`).

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

## E-Mail-Kanal (IMAP)
Ein Postfach je Kanal liefert die Anhänge eingegangener Mails in den Posteingang. Mandanteneinstellungen → *Daten & Zugänge* → *Eingangskanäle* → *E-Mail-Postfach einrichten* (Recht „Einstellungen verwalten").

* **Verbindung:** IMAP, immer verschlüsselt: SSL (IMAPS, Port 993) oder STARTTLS (Port 143, Pflicht). Zertifikat und Servername werden geprüft; Zeitlimits 10 s (Verbindung) und 30 s. Kein Klartext.
* **Absender:** Nur Mails der **freigegebenen Absender** werden übernommen: eine Adresse (`einkauf@kunde.example`) oder eine ganze Domain (`@kunde.example`). Ohne Liste wird nichts übernommen; ein Kanal ohne Absender lässt sich nicht einschalten. Mails anderer Absender werden **ohne Lesen der Anhänge** in den Ordner *LC-abgelehnt* verschoben. (Der Absender einer Mail ist nicht fälschungssicher; die Liste ist ein Filter, kein Echtheitsnachweis – die Dokumente werden wie jeder Upload geprüft und der Eingang bleibt ein Vorschlag.)
* **Ablauf:** Je Lauf höchstens 20 ungelesene Mails, älteste zuerst. Anhänge der Typen PDF, PNG, JPG, TIF bis 10 MB gehen in den Posteingang (Absender im Posteingang „mail:<Kanalname>", Herkunft: Message-ID, Absender, Betreff, Sendezeit). Kleine Inline-Bilder unter 20 KB (Logos in Signaturen), andere Dateitypen und ZIP-Archive werden übergangen. Danach wird die Mail als gelesen markiert und nach *LC-verarbeitet* verschoben (Ordner werden bei Bedarf angelegt; Namen einstellbar). Mails ohne passenden Anhang gelten als verarbeitet; Mails über 40 MB gehen nach *LC-abgelehnt*.
* **Fehler:** Wird eine Datei abgelehnt (z. B. Prüfwert), wandert die Mail nach *LC-abgelehnt*. Ein vorübergehender Fehler (Datenbank, Netz) lässt die Mail ungelesen liegen; sie wird beim nächsten Lauf erneut versucht. Doppelte (gleicher SHA-256) werden nicht noch einmal angelegt.
* **Kennwort:** Wird nur geschrieben, nie angezeigt, und mit AES-256-GCM verschlüsselt gespeichert (Schlüssel `INTAKE_ENCRYPTION_KEY`, mindestens 32 Zeichen, vom Betreiber zu setzen; ohne Schlüssel kann kein Kennwort gespeichert werden). Beim Bearbeiten bedeutet ein leeres Kennwortfeld „unverändert". Der Server ruft nur Hosts auf, die nicht auf interne Adressen zeigen; interne Mailserver müssen in `INTAKE_ALLOWED_HOSTS` (Komma-Liste) freigegeben werden.
* **Einrichten:** Postfach anlegen (der Kanal ist zunächst ausgeschaltet), **Verbindung testen** (zeigt die Zahl ungelesener Mails, liest und ändert nichts), danach einschalten. *Jetzt prüfen* löst einen Lauf sofort aus. Zu Mail-Konten mit Mehrfaktor oder OAuth (z. B. Microsoft 365) siehe „Noch offen".
* REST: wie beim Ordner-Kanal; `POST /api/intake/channels/{id}/test` für den Verbindungstest; Anlegen/Ändern mit `mail` (`host`, `port`, `security`, `username`, `password`, `folder`, `processedFolder`, `rejectedFolder`, `allowedSenders`). Audit: wie beim Ordner-Kanal plus `INTAKE_CHANNEL_TEST`. Migration V119 (`settings`, `secret`).

### Verwaltung, Rechte, Protokoll
REST `GET/POST /api/intake/channels`, `PUT/DELETE /api/intake/channels/{id}`, `POST …/{id}/run`, alle mit dem Recht „Einstellungen verwalten". Anlegen, Ändern, Löschen und manuelle Läufe stehen im Audit-Protokoll
(`INTAKE_CHANNEL_CREATED/UPDATED/DELETED/RUN`). Die Kanäle gehören dem Mandanten und werden mit ihm gelöscht (Tabelle `intake_channel`, Migration V118).

## Noch offen
* **Mail-Anmeldung per OAuth** (Microsoft 365, Google): bisher nur Benutzername und Kennwort (bei Microsoft 365 meist ein App-Kennwort oder ein eigenes Dienstkonto). Push (IDLE) statt Abruf im Minutentakt.
* Benachrichtigung bei Kanälen, die lange nichts liefern; Mail-Text als Notiz zum Dokument.
* Der IMAP-Zugriff ist gegen einen IMAP-Testserver (GreenMail, IMAPS mit Zertifikat) geprüft: ungelesene Mails lesen, Anhänge, als gelesen markieren, in die Ordner *LC-verarbeitet* und *LC-abgelehnt* verschieben (werden angelegt), Wiederholung nach vorübergehendem Fehler, Verbindungstest ohne Änderung, Fehlermeldungen bei falschem Kennwort, fehlendem Ordner und nicht erreichbarem Server. **Nicht geprüft** sind STARTTLS und echte Anbieter (Microsoft 365, Google, eigene Exchange-Server); vor dem produktiven Einsatz mit einem Test-Postfach des Anbieters ausprobieren.
* Optionale Begleitdateien eines Scanners (OCR-Text neben dem Bild) werden nicht ausgewertet; durchsuchbare PDFs reichen.
