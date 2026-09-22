# Corporate LC Manager lokal auf macOS bauen

## Voraussetzungen

- macOS auf Apple Silicon oder Intel
- Java Development Kit 17 oder neuer
- Apache Maven 3.9 oder neuer

Prüfen:

```bash
java -version
mvn -version
```

Fehlt Java, kann Temurin 17 von <https://adoptium.net/temurin/releases/> installiert werden.

Mit Homebrew lassen sich Java und Maven alternativ so installieren:

```bash
brew install --cask temurin@17
brew install maven
```

## Projekt bauen und testen

Im Terminal in den entpackten Projektordner wechseln:

```bash
cd /Pfad/zum/corporate-lc-manager
mvn clean test
mvn clean package
```

Die ausführbare Anwendung liegt danach unter:

```text
target/corporate-lc-manager-0.5.0-SNAPSHOT.jar
```

## Lokal mit H2 starten

Für den lokalen Betrieb ist kein PostgreSQL erforderlich:

```bash
java -jar target/corporate-lc-manager-0.5.0-SNAPSHOT.jar \
  --spring.profiles.active=local
```

Danach im Browser öffnen:

```text
http://localhost:8080
```

Die persistente H2-Datenbank wird im Projektordner unter `data/` angelegt.

Alternativ kann direkt über Maven gestartet werden:

```bash
mvn -Dspring-boot.run.profiles=local spring-boot:run
```

## Mit PostgreSQL starten

Docker Desktop muss laufen:

```bash
docker compose up -d
mvn spring-boot:run
```

Die Standardwerte stehen in `docker-compose.yml` und `src/main/resources/application.yml`.

## In IntelliJ IDEA öffnen

1. IntelliJ IDEA starten.
2. **Open** wählen und den Ordner `corporate-lc-manager` öffnen.
3. Das Maven-Projekt aus `pom.xml` importieren lassen.
4. Als Project SDK Java 17 oder neuer auswählen.
5. `CorporateLcApplication` starten.
6. In der Run Configuration das Profil setzen:

```text
--spring.profiles.active=local
```

## Tests

```bash
mvn test
```

Enthalten sind Tests für die Dokumentenextraktion und die regelbasierte Dokumentenprüfung.

## Wichtige Verzeichnisse

```text
src/main/java                 Java-Quellcode
src/main/resources/static     Weboberfläche
src/main/resources/db         Flyway-Migrationen
src/test/java                 Tests
pom.xml                       Maven-Build
docker-compose.yml            PostgreSQL für den Standardbetrieb
```
