# Corporate LC Manager 0.5

Spring-Boot-MVP für Corporate-Akkreditivmanagement mit Web-Dashboard, MT700/MT707 und Dokumentenakte.

Eine vollständige macOS-Buildanleitung steht in [`BUILD-MACOS.md`](BUILD-MACOS.md). Alternativ baut und startet `build-and-run-macos.command` das Projekt per Doppelklick, wenn Java und Maven installiert sind.

## Stack

- Java 21 / Spring Boot 3.5 / Maven
- Spring Web, Data JPA, Validation
- PostgreSQL + Flyway
- Responsive Web UI ohne separaten Node-Build
- Anmeldung, BCrypt-Passwörter und rollenbasierte Benutzerverwaltung

## Start

### Komplett mit Docker

Die Weboberfläche, Anwendung und PostgreSQL-Datenbank starten gemeinsam:

```bash
docker compose up --build -d
```

Danach ist der Corporate LC Manager unter `http://localhost:8080` erreichbar.

Status und Protokolle anzeigen:

```bash
docker compose ps
docker compose logs -f app
```

Container stoppen (Daten bleiben erhalten):

```bash
docker compose down
```

Für ein anderes Datenbankpasswort oder einen anderen Port können Umgebungsvariablen gesetzt werden:

```bash
DB_PASSWORD='ein-sicheres-datenbankpasswort' ADMIN_PASSWORD='ein-sicheres-adminpasswort' APP_PORT=8090 docker compose up --build -d
```

`ADMIN_PASSWORD` muss beim ersten Start gesetzt sein. Der erste Benutzer heißt standardmäßig `admin`; der Name kann mit `ADMIN_USERNAME` geändert werden. Weitere Benutzer und Rollen werden anschließend über „Benutzer“ in der Oberfläche verwaltet.

Ein vergessenes Administratorkennwort kann einmalig mit `ADMIN_RESET_PASSWORD=true` und einem neuen `ADMIN_PASSWORD` zurückgesetzt werden. Danach muss `ADMIN_RESET_PASSWORD` wieder entfernt oder auf `false` gesetzt werden.

### Lokal ohne PostgreSQL

```bash
mvn -Dspring-boot.run.profiles=local spring-boot:run
```

Das lokale Profil verwendet eine persistente H2-Datenbank unter `./data`.

### Anwendung lokal, PostgreSQL in Docker

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Danach: `http://localhost:8080`

## Funktionen

- Dashboard, Akkreditivübersicht, Suche und LC-Detailansicht
- MT700-Import und strukturierte Darstellung
- MT707-Import, Zuordnung und Amendment-Historie
- Dokumentenakte pro Akkreditiv
- Upload und Download von PDF, XML, Bildern und Textdateien (max. 10 MB)
- Dokumenttypen und optionale Prüfdaten: Datum, Betrag, Währung
- Automatische Textextraktion aus textbasierten PDFs sowie TXT, XML und CSV
- Erkennung von LC-Referenz, Dokumentnummer, Betrag und Währung
- Regelbasierte Vorprüfung:
  - geforderte Standarddokumente vorhanden/fehlend
  - Rechnungsbetrag innerhalb des LC-Betrags
  - Rechnungswährung entspricht der LC-Währung
  - Dokumentdatum liegt nicht nach LC-Ablauf
  - LC-Referenz auf der Rechnung stimmt überein
  - Applicant und Beneficiary sind im Rechnungstext auffindbar
  - doppelte Dokumentnummern werden markiert
  - Scan-PDFs ohne Text werden als OCR-/manueller Prüffall gekennzeichnet
  - unbekannte Anforderungen werden zur manuellen Prüfung markiert

Die automatische Prüfung ist eine Vorprüfung und ersetzt keine fachliche Dokumentenprüfung nach UCP 600/ISBP.

## REST-Endpunkte

- `POST /api/lcs/import/mt700`
- `POST /api/lcs/import/mt707`
- `GET /api/lcs`
- `GET /api/lcs/{id}`
- `POST /api/lcs/{id}/documents` (multipart)
- `GET /api/lcs/{id}/documents`
- `GET /api/documents/{id}/content`
- `GET /api/lcs/{id}/document-checks`

Zum Testen liegen `example-mt700.txt` und `example-mt707.txt` bei.
