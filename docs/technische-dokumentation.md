# Corporate LC Manager – Technische Dokumentation

Stand: 05.10.2026 · Version 0.5.0-SNAPSHOT

## Architektur und Stack

Ein Spring-Boot-Monolith stellt REST-Endpunkte und statische HTML/CSS/
JavaScript-Dateien bereit. Java 17 ist in `pom.xml` und den Docker-Images
festgelegt. Die Oberfläche benötigt keinen Node-Build.

| Baustein | Aufgabe |
|---|---|
| Spring Boot 3.5.6 / Spring Web | Webanwendung und REST |
| Spring Security | Session, Passwort und Rechte |
| Spring Data JPA / Hibernate | Relationale Persistenz |
| PostgreSQL 17 / Flyway | Produktionsdatenbank und Schema-Migrationen |
| H2 | Lokales Entwicklungsprofil und isolierte Tests |
| Apache PDFBox / Poppler | PDF-Verarbeitung und Ausschnitte |
| Tesseract `deu+eng` | OCR für Scans |
| poi-tl / Apache POI | Word-Muster und DOCX |
| ZXing | QR-Codes für TOTP |
| Actuator | Health, Liveness, Readiness und Info |

Die vollständigen Maven-Versionen und Lizenzmetadaten stehen in
`src/main/resources/static/oss-components.json`.

## Modulstruktur

Der Code liegt unter `de.corporate.lc`. Module trennen `api` (Controller/
DTOs), `service` (Geschäftslogik), `domain` (JPA-Entitäten) und `repository`.

| Modul | Verantwortung |
|---|---|
| `lc`, `swift`, `imports` | Akten, Fristen, Änderungen und SWIFT-Import |
| `document`, `check` | Posteingang, Akte, Generierung und Prüfungen |
| `training` | Feldprüfung, Lernregeln, Qualitätsdaten und PDF-Kontext |
| `company` | Firmenstammdaten und Vorlagenzuordnung |
| `user`, `security` | Rollen, Profil, TOTP und Verbindungshinweise |
| `audit`, `messaging` | Ereignisse, CSV-Export und persistente Outbox |
| `monitoring`, `config` | Betriebsübersicht, Health und Konfiguration |

## Datenmodell

Eine `LetterOfCredit` hat Dokumente, Amendments, Notizen und Aufgaben.
Aktenreferenz und Bankreferenzen sind getrennte Attribute. Dokumente und
Trainingssitzungen speichern Originaldateien als `bytea`; extrahierte Texte
und Feld-/OCR-Metadaten werden zusätzlich gespeichert. Vorlagen und
Firmenprofile ergänzen die Dokumentenerstellung.

Eine `TrainingSession` enthält Besitzer, Format, Status (`DRAFT`,
`CONFIRMED`, `DELETED`), Original-PDF, Text, Reviews und optional `lcId`.
`DELETED` blendet Trainingsvorlagen aus; Lerninhalte werden beibehalten.
Andere Dokument-/Aktenlöschungen sind nicht durchgängig Soft-Deletes.

Flyway-Migrationen liegen unter `src/main/resources/db/migration`.
Produktion verwendet `ddl-auto: validate`. Bereits ausgeführte Migrationen
werden nicht geändert; Schemaänderungen erhalten eine neue Migration.

## Avisierungsübernahme und Transaktionen

`AdvisingCaseController` stellt `GET/POST
/api/training/advising/{id}/new-case` bereit. GET erzeugt eine Vorschau aus
persistierten bestätigten Reviews. Ungültige Felder werden verworfen;
Originaltext wird nicht erneut als Quelle für bestätigte Werte verwendet.

POST validiert `AdvisingNewCaseRequest`, Rechte und Ersteller. Der Service
sperrt die Trainingssitzung per `PESSIMISTIC_WRITE`, prüft alle Reviews,
Original-PDF, Status und `lcId`, prüft die Referenz und legt LC/Dokument an.
Die eindeutige DB-Referenz schützt zusätzlich gegen konkurrierende Anlagen.

Das Training wird mit `lcId` verknüpft und bestätigt. Drei Erfolgsereignisse
werden über `AuditService.recordInTransaction` und die Outbox innerhalb
derselben Transaktion gespeichert. Jeder Fehler rollt Akte, Dokument,
Verknüpfung, Erfolgs-Audit und Outbox zurück. Bestehende andere Auditpfade
verwenden überwiegend `REQUIRES_NEW`; deren Semantik ist davon getrennt.

Ein Unit-Test prüft korrigierte/verwarfene/mehrdeutige Werte und
Eigentümergrenzen. `AdvisingCaseTransactionTest` prüft echte JPA-Persistenz,
erneute Übernahme, doppelte Referenzen und Rollback nach bereits
geschriebenen Dokument-/Auditdaten mit einer isolierten H2-Datenbank.

## Erkennung und Lernen

`AdvisingRows` normalisiert beschriftete Brief-/Tabellenzeilen.
`AdvisingLetterExtractor` normalisiert Betrag/Währung und strikt geprüfte
Datumsangaben; unklare Werte bleiben leer. `AdvisingTrainingService` hält
Original- und Korrekturdaten getrennt und speichert Bestätigungen sofort.

`TrainingLearningService` wertet Reviews aus. Avisierungs-Mappings sind
bankprofilbezogen: mindestens drei Stimmen und 75 % Konsistenz. Das
Lernverfahren benötigt keinen externen KI-Dienst. Die Daten stehen zentral
zur Verfügung; dies ist keine Mandantentrennung.

`DocumentExtractionService` erkennt Textebene oder OCR-Bedarf. Scan-PDFs
werden mit 200 DPI, maximal 20 Seiten und Tesseract `deu+eng` verarbeitet.
OCR-Nachweise enthalten Seiten- und Pixelkoordinaten. Trainings-Feldscores
sind nicht dasselbe Datenformat wie Dokument-Wortnachweise und werden nicht
ungeprüft ineinander kopiert. Siehe [OCR-Konfidenz](ocr-confidence.md).

## API-Orientierung

| Bereich | Zentrale Endpunkte |
|---|---|
| Anmeldung | `POST /api/auth/login`, `/api/auth/login/totp`, `/api/auth/logout`; `GET /api/auth/me` |
| Eigenes Konto | `POST /api/auth/password`, `/api/auth/totp/setup`, `/enable`, `/disable` |
| Akten | `GET /api/lcs`, `GET/PUT/DELETE /api/lcs/{id}` |
| SWIFT | `POST /api/lcs/import/mt700`, `/api/lcs/import/mt707`; `/api/imports` mit Vorschau/Historie |
| Dokumente | `GET/POST /api/lcs/{id}/documents`, ZIP-/Batch-Endpunkte, `GET /api/documents/{id}/content` |
| Posteingang | `GET/POST /api/inbox`, `POST /api/inbox/{id}/attach`, `POST /api/inbox/{id}/new-case` |
| Training | `/api/training`, `/preview`, `/{id}/draft`, `/finish`, `/confirm`, Export und Original |
| Avisierungen | `POST /api/training/advising`, `PUT /api/training/advising/{id}`, `GET/POST /api/training/advising/{id}/new-case` |
| Audit | `GET /api/audit`, `GET /api/audit/export.csv` |

Die verbindliche vollständige Zuordnung steht in den Controller-Mappings.
Fehlertexte werden als JSON `error` übergeben. Die neue Übernahme liefert
bei Eingabefehlern 400, bei fehlenden Rechten 403 und bei Datenkonflikten 409.

## Sicherheit und öffentliche Ressourcen

Spring Security verwendet eine serverseitige Session und BCrypt.
`/api/auth/me` liefert den CSRF-Token; schreibende API-Aufrufe senden ihn
im Header. Nur Login und TOTP-Login sind von CSRF ausgenommen.
Der TOTP-Schlüssel wird über `TOTP_ENCRYPTION_KEY` konfiguriert.

Rechte werden aus Rollen auf `PERM_*`-Authorities abgebildet. Die
Avisierungsübernahme verlangt `TRAINING_MANAGE`, `LC_EDIT` und
`DOCUMENT_UPLOAD` sowie ein eigenes Training. Lesen zentraler Trainingsdaten
ist angemeldeten Benutzern erlaubt.

`/info.html`, deren CSS/JavaScript, OSS-Inventar, Lizenz und Drittanbieter-
Hinweise sind öffentlich und enthalten keine Geschäfts- oder Benutzerdaten.
Außerdem sind Login und öffentliche Health-Endpunkte erreichbar. Andere
Anwendungsressourcen erfordern eine Anmeldung. Frame-Einbettung wird gesperrt.

## Konfiguration und lokaler Start

Grundkonfiguration: `src/main/resources/application.yml`.
Lokales Profil: `application-local.yml` mit persistenter H2-Datenbank in `data`.

```bash
mvn -Dspring-boot.run.profiles=local spring-boot:run
docker compose up --build -d
```

Wichtige Variablen:

| Variable | Verwendung |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Datenbankverbindung |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Initialer Administrator |
| `ADMIN_RESET_PASSWORD` | Einmaliger Admin-Reset; anschließend deaktivieren |
| `TOTP_ENCRYPTION_KEY` | Verschlüsselung der TOTP-Secrets |
| `SMTP_ENABLED`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` | E-Mail-Versand |
| `SMTP_AUTH`, `SMTP_STARTTLS` | SMTP-Authentifizierung und STARTTLS |
| `OUTBOX_INTERVAL_MS`, `OUTBOX_MAX_ATTEMPTS` | Ereignisversand und Wiederholungen |
| `LC_OCR_CONFIDENCE_THRESHOLD` | OCR-Prüfschwelle für neue Extraktionen |

Die Produktions-Compose-Datei verlangt DB-Passwort, Admin-Passwort und
TOTP-Schlüssel. Secrets und Originalgeschäftsdokumente gehören nicht in Git.

## Produktion, Sicherung und Wiederherstellung

Produktion: `https://lc.example.com`; Projektpfad auf dem Server:
`/opt/corporate-lc-manager`.
SSH-Alias: `lc-server`.

`docker-compose.prod.yml` startet Anwendung und PostgreSQL. Die Anwendung
läuft als unprivilegierter Benutzer auf Port 8080 im Proxy-Netz
`lc_proxy`; TLS wird vom vorgeschalteten Proxy bedient.

```bash
./scripts/backup-prod.sh
./scripts/deploy-prod.sh
```

Das Deployment sichert die DB, aktualisiert `main` per Fast-Forward, baut
das Image und wartet auf Container-Health. Der Docker-Build überspringt
Tests; diese werden zuvor lokal ausgeführt. Backups liegen unter `backups/`
als PostgreSQL-Custom-Dumps mit restriktiven Dateirechten. Die vorhandenen
Skripte ersetzen keine externe Backup-Aufbewahrung oder regelmäßige
Wiederherstellungsprobe.

Für eine Restore-Probe einen Dump in eine getrennte PostgreSQL-Datenbank
einspielen und Anwendung/Dokumentabruf prüfen. Produktions-Restores benötigen
einen abgestimmten Wartungsablauf und eine aktuelle Sicherung. `.env`,
TOTP-Schlüssel und Proxy-Konfiguration zusätzlich geschützt sichern.

## Health und Monitoring

- `/api/health`: öffentliches `UP`/Health-Signal.
- `/actuator/health`: allgemeiner Health-Status.
- `/actuator/health/liveness`: Prozess-Liveness.
- `/actuator/health/readiness`: Bereitschaft einschließlich Datenbank.
- `/actuator/info`: Administratorzugang; ohne konfigurierte Info-Daten ggf. leer.

Actuator-Health-Details werden nicht öffentlich ausgegeben. Die
Betriebsübersicht und Outbox-Administration liegen unter `/api/admin/**`
und benötigen Administratorzugang. Es gibt keinen aktivierten RabbitMQ-
Adapter; der derzeitige Publisher ist ein lokaler Log-Adapter.

## Lizenz- und OSS-Pflege

Copyright © 2026 Markus Schorpp. Projektlizenz: GPL-3.0-or-later.
Der vollständige GPL-v3-Text liegt in `LICENSE` und öffentlich in
`/license.txt`; [gnu.org](https://www.gnu.org/licenses/gpl.html) führt zur
aktuellen GPL. Drittanbieter behalten ihre eigenen Rechte und Lizenzen.

Das OSS-Inventar umfasst 127 aufgelöste Java-Laufzeitkomponenten. Zur
Aktualisierung nach einer Dependency-Änderung:

```bash
mvn dependency:list -DincludeScope=runtime -DoutputFile=/tmp/lc-dependencies.txt
python3 scripts/update-oss-inventory.py /tmp/lc-dependencies.txt
```

Der Generator liest Artefakt-/Parent-POMs und übernimmt die eingebetteten
Lizenz-/NOTICE-Dateien aus den JARs. Ungeklärte Lizenzmetadaten stoppen die
Erzeugung. Native Docker-Komponenten werden gesondert auf der Info-Seite
benannt; ihre Versionen und Systemlizenzen beim Image-Update nachprüfen.

## Prüfung vor Auslieferung

```bash
mvn test
mvn -DskipTests package
node --check src/main/resources/static/advising-training.js
node --check src/main/resources/static/advising-new-case.js
node --check src/main/resources/static/info.js
git diff --check
```

Zusätzlich den Trainingsdialog, die Anlage-Maske, Fehlerfall bei doppelter
Referenz, Original-PDF in der Akte und die öffentliche Info-Seite im Browser
prüfen. Reale OCR-Tests benötigen installierte Tesseract-/Poppler-Werkzeuge;
einzelne Umgebungstests können ohne diese Voraussetzungen übersprungen werden.

## Passwort-Neuanforderung

- Öffentliche Seite `/password-reset.html`; öffentliche POST-Endpunkte
  `/api/auth/password-reset/request` und `/api/auth/password-reset/complete`.
- Anforderungen verwenden Benutzername und gespeicherte Kontaktadresse. Unbekannte,
  gesperrte oder nicht passende Konten und gedrosselte Anforderungen erhalten dieselbe
  Antwort. Mailversand läuft in einem begrenzten Executor (2 Threads, Queue 50), nicht
  in der HTTP-Antwort. Der öffentliche Request ist deshalb auch bei SMTP-Ausfällen neutral.
- 32 kryptografisch zufällige Bytes ergeben einen 43-stelligen URL-safe Token. Nur dessen
  SHA-256-Hash wird in `password_reset_token` (Migration V40) gespeichert. Gültigkeit:
  30 Minuten. Link im URL-Fragment; die Seite entfernt es sofort aus der Adresszeile.
  Keine Tokens in Querystrings, Audit, Browserstorage oder Provider-Fehlermeldungen.
- `PUBLIC_BASE_URL` ist die feste HTTPS-Basis für E-Mails (Produktion: `https://lc.example.com`).
  Links werden niemals aus dem eingehenden Host-Header erzeugt. Der bestehende
  `SMTP_ENABLED`-/`SMTP_FROM`-/SMTP-Zugang wird verwendet; Zeitlimits je SMTP-Operation 5 s.
- Reset serialisiert am Benutzer-Datensatz und aktualisiert Passwort, Token-Verbrauch
  und Erfolgs-Audit in einer Transaktion. Ein zweiter gleichzeitiger Verbrauch scheitert.
  Neue Anforderungen, geänderte Passwörter oder Kontaktadressen entwerten vorherige Links.
- Rate-Limit im Arbeitsspeicher dieser einzelnen Instanz: pro 15 Minuten 3 Anforderungen
  je Benutzername und 10 je Client-IP; Abschlussversuche 10 je IP. Identifikatoren sind
  gehasht, Map und Versandqueue begrenzt. Ein Neustart setzt Limits zurück; für mehrere
  Instanzen ist ein gemeinsamer Limiter notwendig. Reverse-Proxy-Header nur aus
  vertrauenswürdigen Proxys übernehmen, damit Client-IP-Limits nicht umgangen werden.
- Authentifizierte Sessions speichern einen Hash des aktuellen Passwort-Hashes.
  `CredentialSessionFilter` prüft ihn vor weiteren Zugriffen. Nach Reset oder sonstiger
  Passwortänderung werden alte Sessions bei der nächsten Anfrage invalidiert. Auch ein
  bereits begonnener TOTP-Login prüft den Passwortstand vor dem zweiten Faktor.
  TOTP-Geheimnis und Recovery-Codes werden nicht verändert, kein Auto-Login nach Reset.
- Bei der ersten Auslieferung werden bestehende Sessions ohne Credential-Stamp einmalig
  abgemeldet. Benutzer müssen sich neu anmelden. Ein schon laufender Request wird nicht
  rückwirkend abgebrochen. Die Passwortregel bleibt Groß-/Kleinbuchstaben und Zahl bei
  mindestens 10 Zeichen; Reset erlaubt höchstens 72 UTF-8-Bytes (BCrypt-Grenze).
- Audit-Aktionen: `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_MAIL_FAILED`,
  `PASSWORD_RESET_FAILED`, `PASSWORD_RESET_COMPLETED`; niemals Passwort oder Token.
  Es wurden keine echten Reset-Mails oder Passwortänderungen an Produktionskonten
  zum Testen ausgelöst. Tests verwenden Mail-Mocks und eine isolierte H2-Datenbank.
