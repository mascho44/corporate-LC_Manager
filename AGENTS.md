# AGENTS.md – Hinweise für Coding-Agenten

Corporate LC Manager: mandantenfähige Spring-Boot-Anwendung (Java 17, Maven, PostgreSQL, Flyway) mit Weboberfläche aus klassischem JavaScript ohne Node-Build.
Fachliche und technische Beschreibung: `README.md`, `docs/README.md` (Übersicht), `docs/technische-dokumentation.md`, `ROADMAP.md`.

## Bauen und testen

```bash
mvn -o test                      # Java-Tests (Integrationstests gegen PostgreSQL laufen in der CI als „Tenant acceptance“)
node --test src/test/js/*.cjs    # JavaScript-Tests der Oberfläche
```

Vor jedem Pull Request laufen beide Befehle. Die CI hat acht Prüfungen (CodeQL ×4, Build & tests, Dependency review, Tenant acceptance, Browser smoke test); alle müssen grün sein.
Den Browser-Rauchtest (`scripts/browser-smoke.cjs`) gibt es nur in der CI. Für Layout-Prüfungen: `scripts/layout-stack.sh up|code|down` und `docs/layout-pruefung.md`.

## Arbeitsweise

- Eine Änderung je Branch und Pull Request, Commit- und PR-Texte deutsch, ohne Umlaute im Commit-Titel (`ue`, `ae`, `oe`).
- Jede sichtbare Änderung bekommt einen Eintrag in `CHANGELOG.md` (oben unter dem Abschnitt „in Arbeit“) und, wenn sie Fachlichkeit betrifft, einen Absatz in `docs/`.
- **Nicht ohne ausdrückliche Freigabe mergen oder deployen.** Nach grüner CI berichten und fragen.
- Deployment (nur auf Freigabe): auf dem Server `git pull --ff-only` und `./scripts/deploy-prod.sh`. Das tar-basierte Ausrollen löscht entfernte Dateien nicht.
- Zugangsdaten, Kennwörter und Schlüssel aus `.env` nie verwenden oder ausgeben; Testwerte nur aus Seed-Dateien.

## Mehrsprachigkeit (Pflicht)

Jeder neue sichtbare Text ist von Anfang an deutsch und englisch:
- Oberfläche: `tx(key, 'deutscher Text', werte)` bzw. `data-i18n`, Schlüssel in `language-de.json` **und** `language-en.json`.
- Servermeldungen (Fehlertexte): englische Entsprechung in `server-messages-en.json`; `ServerMessagesTest` schlägt sonst fehl.
- Seiten vor der Anmeldung: `prelogin-language.js`.

## Oberfläche

- Skripte teilen den globalen Gültigkeitsbereich; die Ladereihenfolge steht in `index.html` und wird in `script-order.test.cjs` geprüft. `app.js` ist in `app-*.js` zerlegt, Tests lesen den Quelltext über `src/test/js/app-source.cjs`.
- Geänderte Dateien brauchen eine neue Versionsnummer (`?v=…`) in `index.html` bzw. `platform.html`, sonst liefert der Browser den Cache.
- Stile: `ui-system.css` wird zuletzt geladen; punktuelle Korrekturen in `compact.css` mit der Selektorform `html body …`.
- Content-Security-Policy: keine Inline-Skripte, stattdessen eigene Dateien.

## Backend

- Mandantentrennung über PostgreSQL Row-Level-Security (`app.tenant_id`); Entitäten erben von `TenantOwnedEntity`, Repositories von `TenantScopedRepository`. Neue Tabellen mit `tenant_id`, RLS-Richtlinie und Eintrag in der Löschfunktion für Mandanten (Migration).
- Migrationen: nächste freie Nummer unter `src/main/resources/db/migration/`, bestehende nie ändern.
- Module je Mandant (Bank-Profil usw.) steuert `TenantModulePolicy` nach Pfad.
- Berechtigungen in `SecurityConfig`; schreibende Endpunkte brauchen ein eigenes Recht.
- Prüfbefunde (`CheckResult`): automatischer Hinweis (`automaticSeverity`) und fachliche Entscheidung (Bestätigung mit Prüfer und Begründung) bleiben getrennt; `explanation` enthält die erklärende Rechnung in Worten.
