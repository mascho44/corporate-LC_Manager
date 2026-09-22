# Corporate LC Manager vollständig auf den Mac übertragen

Dieses Projektpaket enthält den vollständigen Arbeitsstand: Quellcode, Tests,
Maven-Konfiguration, lokale Maven-Laufzeit, Build-Ergebnisse, ausführbare JAR,
Beispieldateien und die lokale H2-Datenbank.

## 1. Projekt dauerhaft ablegen

1. Das ZIP auf dem Mac vollständig entpacken.
2. Den Ordner `corporate-lc-manager` zum Beispiel nach
   `~/Documents/Projekte/` verschieben.
3. Nicht direkt innerhalb des ZIP-Archivs arbeiten.

## 2. Fertige Anwendung starten

Im Finder `start-macos.command` doppelklicken. Falls macOS den ersten Start
blockiert: Rechtsklick auf die Datei, **Öffnen**, anschließend erneut
**Öffnen** wählen.

Die Anwendung ist danach unter <http://localhost:8080> erreichbar. Die lokale
Datenbank liegt in `data/lcmanager.mv.db`. Dieses Terminalfenster während der
Nutzung geöffnet lassen; mit `Ctrl+C` wird die Anwendung beendet.

## 3. Projekt entwickeln und neu bauen

Voraussetzung ist Java 17 oder neuer. Danach im Terminal:

```bash
cd ~/Documents/Projekte/corporate-lc-manager
./build-and-run-macos.command
```

Alternativ kann der Ordner in IntelliJ IDEA über die Datei `pom.xml` als
Maven-Projekt geöffnet werden. Das Spring-Profil `local` verwendet die
mitgelieferte H2-Datenbank; PostgreSQL kann mit `docker-compose.yml` genutzt
werden.

## Datensicherung

Vor größeren Änderungen genügt eine Kopie des gesamten Projektordners. Für
eine reine Datensicherung bei beendeter Anwendung kann zusätzlich der Ordner
`data` kopiert werden.
