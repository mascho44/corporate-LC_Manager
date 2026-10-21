# Erstinstallation: Leitfaden für Betrieb und erste Benutzer

Dieser Leitfaden führt von einem leeren Server bis zum ersten Mandanten mit eigenen Benutzern und der ersten Akte. Teil A richtet sich an den technischen Betrieb, Teil B an die Person, die die Anwendung fachlich einrichtet. Er beschreibt die Produktivinstallation mit Docker Compose. Für lokale Entwicklung siehe das [README](../README.md).

Die Angaben beziehen sich auf Version 0.5. Wo ein Wert vom Standard abweicht oder noch nicht eingerichtet ist, steht das ausdrücklich dabei.

---

## Teil A: Technische Installation

### A1. Voraussetzungen

| Voraussetzung | Hinweis |
|---|---|
| Linux-Server mit Docker und Docker Compose (v2) | Benutzer ohne Root, in der Gruppe `docker` |
| Git, `openssl`, `curl` | `openssl` erzeugt die Schlüssel |
| Arbeitsspeicher und Platte | Die Anwendung ist auf 2 GB RAM und 2 CPUs begrenzt (`docker-compose.prod.yml`), dazu PostgreSQL. Platte für Datenbank und Sicherungen einplanen (eine Sicherung ist so groß wie die Datenbank) |
| Reverse-Proxy mit TLS | Die Anwendung selbst spricht nur HTTP auf Port 8080 **im Docker-Netz**. HTTPS endet am Proxy. Der Port 8080 wird nicht öffentlich veröffentlicht |
| Eine öffentliche HTTPS-Adresse | Wird als `PUBLIC_BASE_URL` gesetzt (Links in Einladungsmails, Cookie-Einstellungen) |
| Optional: Mailserver (SMTP) | Für Einladungen und Passwort-Reset **nötig**, siehe A6 |
| Optional: `restic` und ein externes Ziel | Für Offsite-Sicherung, siehe A8 |

### A2. Code holen

```bash
git clone https://github.com/mascho44/corporate-LC_Manager.git corporate-lc-manager
cd corporate-lc-manager
```

Der Produktivstand kommt aus dem Branch `main`. Auf dem Server liegen keine lokalen Änderungen (das Deploy-Skript lehnt das ab).

### A3. Proxy-Netzwerk

Die Anwendung hängt zusätzlich an einem vorhandenen Docker-Netzwerk, in dem der Reverse-Proxy läuft, unter dem Alias `lc-manager-app`. Der Name des Netzwerks wird als `PROXY_NETWORK` angegeben. Gibt es noch keines:

```bash
docker network create proxy
```

Der Proxy leitet `https://<Ihre-Adresse>` an `http://lc-manager-app:8080` weiter. Er muss

- fremde `X-Forwarded-For`/`Forwarded`-Header **entfernen oder vertrauenswürdig neu setzen** (die Anmelde-Sperren nach IP-Adresse hängen davon ab),
- nur HTTPS ausliefern (die Session-Cookies sind `Secure`).

### A4. Konfiguration (`.env`)

Die Datei `.env` im Projektordner enthält alle Geheimnisse. Sie wird nie in Git eingecheckt und bekommt Rechte 600.

```bash
umask 077
cat > .env <<EOF
DB_PASSWORD=$(openssl rand -base64 24)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=$(openssl rand -base64 18)
TOTP_ENCRYPTION_KEY=$(openssl rand -base64 48)
PUBLIC_BASE_URL=https://lc.example.com
PROXY_NETWORK=proxy
EOF
chmod 600 .env
```

| Variable | Bedeutung |
|---|---|
| `DB_PASSWORD` | Passwort der Datenbank (Pflicht) |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Erster Benutzer, wird beim **ersten Start** angelegt (Pflicht). Nur der Name `admin` wird zum **Plattformadministrator** |
| `TOTP_ENCRYPTION_KEY` | Verschlüsselt die Zwei-Faktor-Geheimnisse und Einladungsdaten, **mindestens 32 Zeichen** (Pflicht). **Sicher aufbewahren, nicht ändern**: Ein Verlust macht eingerichtete Authenticator-Apps unbrauchbar |
| `PUBLIC_BASE_URL` | Öffentliche HTTPS-Adresse ohne Schrägstrich am Ende (Pflicht) |
| `PROXY_NETWORK` | Name des Docker-Netzes des Proxys (Pflicht) |

Das Passwort in `ADMIN_PASSWORD` wird nur beim ersten Start gelesen, muss aber in der Datei stehen bleiben, weil die Compose-Datei es verlangt. Ändern Sie das Passwort nach der ersten Anmeldung in der Anwendung (B1).

**Sichern Sie die `.env` an einem getrennten Ort** (zum Beispiel Passwortmanager). Sie enthält den Schlüssel, ohne den Sicherungen der Datenbank unvollständig wären.

Weitere, optionale Variablen stehen in `docker-compose.prod.yml` (SMTP, ClamAV-Virenscan, KI-Vorschläge, EBICS, Eingangskanäle, Aufbewahrungsdauer des Audit-Protokolls `AUDIT_RETENTION_YEARS` mit Standard 10 Jahre).

### A5. Erster Start

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --build
docker compose --env-file .env -f docker-compose.prod.yml ps
docker compose --env-file .env -f docker-compose.prod.yml logs app | grep "Initial administrator"
```

Der erste Start baut das Image (einige Minuten), legt das Datenbankschema per Flyway an und erzeugt den ersten Administrator. Die Anwendung ist bereit, wenn der Dienst `app` als `healthy` gemeldet wird. Prüfen Sie von außen:

```bash
curl -fsS https://lc.example.com/api/health
```

Danach öffnen Sie `https://lc.example.com` im Browser (die Anmeldeseite erscheint).

### A6. Mailserver (für Einladungen und Passwort-Reset)

Neue Benutzer entstehen **nur über Einladungen** per E-Mail (24 Stunden gültiger Einmal-Link). Setzen Sie dafür in der `.env`:

```text
SMTP_ENABLED=true
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
SMTP_FROM=lc-manager@example.com
```

und starten Sie neu (`scripts/deploy-prod.sh`, siehe A9). Ohne Mailserver kann die Installation laufen, aber niemand außer `admin` kann sich anmelden. Die Einladungsmails werden zusätzlich in einer Warteschlange wiederholt versendet; ihren Zustand zeigt der Operationsmonitor.

### A7. Datenbankrollen trennen (empfohlen)

Standardmäßig nutzt die Anwendung für Migrationen und Laufzeit dieselbe Rolle `lcmanager`, die in der Produktion Superuser ist. Besser läuft die Anwendung mit einer eingeschränkten Rolle ohne Schema-Rechte. Dazu in der `.env` ergänzen:

```text
DB_APP_USER=lcmanager_app
DB_APP_PASSWORD=<mindestens 24 zufällige Zeichen>
```

dann einmalig `bash scripts/db-app-role.sh` ausführen und neu starten. Details und Grenzen: [Datenbankrollen](datenbank-rollen.md). Nach Updates, die neue Datenbankfunktionen mitbringen, das Skript erneut ausführen.

### A8. Sicherung

Eine Sicherung entsteht automatisch vor jedem Deploy. Zusätzlich:

1. **Passwortdatei für verschlüsselte Sicherungen** anlegen und getrennt vom Server aufbewahren:

   ```bash
   install -m 600 /dev/null ~/.lcm-backup-passphrase
   openssl rand -base64 48 > ~/.lcm-backup-passphrase
   ```

   Ohne diese Datei sind verschlüsselte Sicherungen **nicht wiederherstellbar**. Ist sie vorhanden, verschlüsselt `scripts/backup-prod.sh` automatisch (`.dump.enc`).

2. **Tägliche Sicherung** per Cron (Benutzer des Projekts, `crontab -e`):

   ```text
   30 3 * * * PATH=/usr/local/bin:/usr/bin:/bin BACKUP_KEEP=30 /pfad/zu/corporate-lc-manager/scripts/backup-prod.sh >> $HOME/lcm-backup.log 2>&1
   ```

   `BACKUP_KEEP=30` behält die 30 neuesten Sicherungen. Ohne die Variable wird nie gelöscht.

3. **Wiederherstellung testen**, bevor Sie sie brauchen:

   ```bash
   bash scripts/test-backup-restore.sh /pfad/zu/backups/lcmanager-<Zeitstempel>.dump.enc
   ```

   Die Sicherung wird in einer isolierten Testdatenbank eingespielt. Erst „Wiederherstellung in isolierter Testdatenbank erfolgreich“ zählt.

4. **Offsite-Sicherung (nicht automatisch eingerichtet).** Sicherungen auf demselben Server schützen nicht vor Verlust des Servers oder der Platte. Dafür gibt es `scripts/backup-offsite.sh` mit `restic` (Ziel, Passwortdatei und Zeitplan richten Sie selbst ein, siehe [Betriebssicherheit](betriebssicherheit.md)). Für Testbetrieb kann das entfallen; für echte Daten sollte es eingerichtet sein.

### A9. Audit-Anker und Updates

- **Audit-Anker** (empfohlen): `scripts/audit-anchor.sh` legt den Kopf der Audit-Hash-Kette außerhalb der Datenbank ab und erkennt so nachträgliche Manipulation. Stündlich per Cron: `7 * * * * cd /pfad/zu/corporate-lc-manager && ./scripts/audit-anchor.sh >> $HOME/audit-anchors/cron.log 2>&1`.
- **Update einspielen:** `./scripts/deploy-prod.sh`. Das Skript sichert die Datenbank, holt den Stand aus `main`, baut neu, startet und wartet auf „healthy“. Schlägt der Start fehl, zeigt es die Protokolle.
- **Beobachten:** In der Anwendung zeigt der Menüpunkt „Operationsmonitor“ den Zustand (Warteschlangen, Fehler). Zusätzlich sollten Sie auf dem Server Platte, Zertifikat und Container überwachen (zum Beispiel per Cron-Skript mit Mail bei Auffälligkeiten).

### A10. Abnahme-Checkliste (technisch)

- [ ] `https://…/api/health` antwortet, Anmeldeseite erreichbar, kein Zugriff über HTTP
- [ ] `.env` hat Rechte 600 und liegt zusätzlich sicher getrennt
- [ ] Anmeldung als `admin` klappt, Zwei-Faktor eingerichtet (B1)
- [ ] Datenbankrolle getrennt (A7) oder bewusst nicht
- [ ] Tägliche Sicherung läuft, Wiederherstellung einmal getestet, Passwortdatei getrennt gesichert
- [ ] Audit-Anker per Cron
- [ ] Mailversand getestet (Einladung an die eigene Adresse)

---

## Teil B: Erste Benutzer und erste Akte

### B1. Erste Anmeldung als Plattformadministrator

1. Öffnen Sie die Adresse der Anwendung und melden Sie sich als `admin` mit dem Passwort aus `ADMIN_PASSWORD` an.
2. Administratoren benötigen **Zwei-Faktor-Anmeldung**. Die Anwendung zeigt einen QR-Code: mit einer Authenticator-App scannen und den Code eingeben.
3. Die Anwendung zeigt **acht Notfallcodes einmalig**. Speichern Sie sie sicher außerhalb der Anwendung. Administratoren können die Zwei-Faktor-Anmeldung nicht abschalten.
4. Ändern Sie das Passwort (Plattformverwaltung, „Eigenes Passwort ändern“). Danach ist eine neue Anmeldung nötig.

Der Plattformadministrator verwaltet die Anwendung, aber **keine Geschäftsdaten**. Er landet nach der Anmeldung direkt in der Plattformverwaltung (`/platform.html`) und bekommt keine Mandantenmitgliedschaft.

### B2. Ersten Mandanten anlegen

Ein Mandant ist ein getrennter Arbeitsbereich (zum Beispiel eine Firma). In der Plattformverwaltung, Reiter **Mandanten**:

1. **Mandant anlegen**: Code (kurz, Kleinbuchstaben und Ziffern), Name, **Profil** (Corporate, Bank oder beides).
2. **Ersten Mandantenadministrator** angeben: Benutzername, Name und E-Mail-Adresse. Ohne diese Angabe wird kein Mandant angelegt.
3. Die Anwendung verschickt eine **Einladung** (Link, 24 Stunden gültig).

Das Profil bestimmt, welche Funktionen sichtbar sind. Corporate-Mandanten erhalten Dokumentenerstellung, Schnellprüfung, Beschaffungsplan und Einreichungspaket; die ausführliche Bank-Dokumentenprüfung ist dort nicht verfügbar.

### B3. Einladung annehmen

Die eingeladene Person öffnet den Link, wählt ihr Passwort und richtet bei Administratorrechten ebenfalls die Zwei-Faktor-Anmeldung ein (Notfallcodes sichern). Danach meldet sie sich an, wählt auf der Anmeldeseite den **Mandanten** und arbeitet im Mandanten.

Hat eine E-Mail-Adresse bereits ein Konto, wird keine Einladung verschickt; der Plattformadministrator kann das Konto dem Mandanten zuweisen (Plattformverwaltung, „Benutzer & Mandanten“).

### B4. Mandanten einrichten (Mandantenadministrator)

In dieser Reihenfolge:

1. **Firmenstammdaten** (Menü „Firmenstammdaten“): Name, Anschrift, Kontakt, Steuer- und Registernummer, Bankverbindung und Logo. Sie bilden den Briefkopf und die Fußzeile aller erzeugten Dokumente (Rechnung, Packliste, Zertifikate, Begleitschreiben). Ohne Firmenprofil erscheint nur der Begünstigte aus dem Akkreditiv.
2. **Sprache**: Standardsprache des Mandanten (Menü „Mandant“, Stammdaten). Jeder Benutzer kann in seinem Profil eine eigene Sprache wählen (Deutsch oder Englisch).
3. **Dokumentvorlagen** (optional): Die eingebauten Standardvorlagen reichen für den Anfang. Eigene Word-Vorlagen laden Sie unter „Dokumentvorlagen“ hoch, siehe [Word-Vorlagen](word-vorlagen.md).
4. **Benutzer einladen** (Menü „Benutzer“, Reiter „Einladungen“): Name, E-Mail, Rolle. Mehrere Personen lassen sich auf einmal einladen.
5. **Kundenstamm** (optional, Menü „Mandant“, Reiter „Daten & Zugänge“) und **Eingangskanäle** wie Erfassungs-Links für Spediteure oder ein Postfach (siehe [Eingangskanäle](eingangskanaele.md), [Erfassungszugang](erfassungszugang.md)).
6. **Freigabestufen** (optional): Anzahl erforderlicher Freigeber je Währung und Betrag für Dokumententwürfe.

### B5. Rollen

Es gibt Systemrollen auf Basis von **Administrator**, **Bearbeiter (Editor)**, **Betrachter (Viewer)** und **Benutzer**, dazu eigene Rollen. Die Rechte jeder Rolle zeigt die Matrix unter „Benutzer“, Reiter „Rollen & Rechte“. Sinnvolle Aufteilung für den Anfang:

| Person | Rolle |
|---|---|
| Fachliche Leitung / Einrichtung | Administrator |
| Sachbearbeitung (Akten, Dokumente, Einreichung) | Bearbeiter |
| Lesender Zugriff (Geschäftsleitung, Revision) | Betrachter |

Für sensible Schritte (Freigabe von Dokumententwürfen) gilt das Vier-Augen-Prinzip: Die Person, die ein Dokument erstellt hat, darf es nicht selbst freigeben. Rechte können Sie je Rolle einzeln anpassen. Mehr: [Benutzer & Rechte](benutzer-und-rechte.md).

### B6. Erste Akte

1. **Akkreditiv importieren**: Menü „SWIFT importieren“ → MT700-Text einfügen oder eine PDF/Textdatei wählen. Zum Ausprobieren liegt `example-mt700.txt` im Projekt. Alternativ landet ein Avis im Posteingang und lässt sich als neue Akte einlesen (siehe [Avis einlesen](avis-einlesen.md)).
2. **Rolle in der Akte prüfen**: Sind Sie Begünstigter (Exporteur) oder Antragsteller (Importeur)? Die Auswahl steht oben in der Akte und steuert, ob der Beschaffungsplan gilt.
3. **Beschaffungsplan ansehen**: Er zeigt, welche Dokumente das Akkreditiv verlangt, was schon da ist und bis wann was fällig ist (nach dem spätesten Verladetag). Vorlaufzeiten pro Dokumentart lassen sich einstellen.
4. **Dokumente erzeugen**: „Dokumentensatz erzeugen“ erstellt Rechnung, Packliste und die geforderten Zertifikate aus einer Eingabe, wahlweise als Word oder PDF. Die Anwendung warnt, wenn die Rechnung den zulässigen Höchstbetrag überschreitet.
5. **Dokumente von Dritten**: Hochladen oder über den Posteingang zuordnen. Entwürfe (zum Beispiel ein Konnossement vom Spediteur) lassen sich mit „Entwurf prüfen“ vorab gegen das Akkreditiv prüfen.
6. **Schnellprüfung**: Die Einreichungskarte zeigt „Einreichungsbereit“ oder die offenen Punkte mit Hinweisen zur Behebung.
7. **Einreichungspaket**: Erstellt das Begleitschreiben an die Bank mit Dokumentenliste, Originalen und Kopien und die Dokumente in der üblichen Reihenfolge als ZIP.
8. **Fristen**: Ablauf, Verladetag und Vorlagefrist erscheinen im Fristenmonitor und im Fristenkalender; als Kalenderdatei (.ics) mit Erinnerungen lassen sie sich in den eigenen Kalender übernehmen.

Die automatische Prüfung ist eine **Vorprüfung** und ersetzt nicht die Prüfung durch die Bank.

### B7. Abnahme-Checkliste (fachlich)

- [ ] Mandant angelegt, Profil stimmt (Corporate/Bank)
- [ ] Mandantenadministrator hat die Einladung angenommen und Zwei-Faktor eingerichtet
- [ ] Firmenstammdaten mit Logo vollständig, ein erzeugtes Dokument sieht richtig aus
- [ ] Mindestens ein Bearbeiter und ein Betrachter eingeladen und getestet
- [ ] Erste Akte importiert, Dokumentensatz erzeugt, Einreichungspaket heruntergeladen
- [ ] Sprache der Benutzer gewählt (Deutsch/Englisch)

---

## Wenn etwas nicht klappt

| Problem | Lösung |
|---|---|
| Container wird nicht „healthy“ | `docker compose --env-file .env -f docker-compose.prod.yml logs app`. Häufig fehlt eine Pflichtvariable (`ADMIN_PASSWORD`, `TOTP_ENCRYPTION_KEY`, `PUBLIC_BASE_URL`, `PROXY_NETWORK`) |
| Anmeldung wird mit Fehler 429 abgelehnt | Zu viele Fehlversuche, nach fünf Minuten erneut versuchen |
| Administratorpasswort vergessen | `ADMIN_RESET_PASSWORD=true` und ein neues `ADMIN_PASSWORD` setzen, neu starten, danach `ADMIN_RESET_PASSWORD` wieder entfernen |
| Authenticator-App verloren | Einen der acht Notfallcodes verwenden. Ohne Notfallcode ist ein Eingriff des Betriebs nötig, deshalb die Codes sicher aufbewahren |
| Eingeladene Person erhält keine Mail | `SMTP_*` prüfen (A6), Spam-Ordner, Zustand der Warteschlange im Operationsmonitor, Einladung erneut senden |
| Platte voll | Sicherungen prüfen (`BACKUP_KEEP` setzen), `docker system df` |
| Weitere Hilfe | [Technische Dokumentation](technische-dokumentation.md), [Anmeldesicherheit](anmeldesicherheit.md), [Plattformverwaltung](platform-administration.md), [Betriebssicherheit](betriebssicherheit.md) |
