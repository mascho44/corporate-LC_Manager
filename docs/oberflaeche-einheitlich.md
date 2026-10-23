# Einheitliche Bedienelemente

Alle Schaltflächen, als Schaltfläche gestaltete Links, Auswahllisten (Dropdowns) und einzeilige Eingabefelder haben dieselbe Größe,
Schriftgröße und Rundung. Die Werte stehen einmalig in `ui-system.css` (geladen als letztes Stylesheet in `index.html` und
`platform.html`; Test: `ui-system.test.cjs`):

| | Standard | Klein (Zeilen, Tabellen, Listen) |
|---|---|---|
| Höhe | 36 px | 30 px |
| Schrift | 14 px | 13 px |
| Rundung | 8 px | 8 px |

- „Klein“ gilt automatisch für Bedienelemente in Zeilen (`.membership-row`, `.queue-row`, `.pu-row` …), Tabellen und den Aktionsleisten
  der Dokument- und Prüflisten; es muss nichts ausgezeichnet werden.
- Ausgenommen sind Navigation, Reiter, Symbolschaltflächen (`.ghost`), Vorschaubilder und der Zoom-Griff.
- Neue Seiten verwenden einfach `button`, `select`, `input`; seitenspezifische Größen (`min-height`, Innenabstände an Bedienelementen)
  werden nicht mehr gesetzt, sie würden vom Standard überschrieben.
- Farben und Varianten (primär, `.secondary`, `.danger`, `.ghost`) bleiben unverändert (`app-polish.css`).

Vorher waren Schaltflächen 44 px hoch mit Schrift zwischen 11 und 16 px, Eingabefelder 40 oder 44 px, Rundungen zwischen 0 und 10 px.

## Menü: Arbeitsbereich und Administration
Das Menü trennt **Bearbeitung** von **Administration** (`nav-groups.js`). Die Einträge werden von den Seiten selbst angelegt; die Reihenfolge wird zentral gesetzt und gilt bei jeder Änderung des Menüs neu:
- **ARBEITSBEREICH:** Cockpit, Meine Arbeit, Gruppeninbox, LC-Akten, Garantien, Linien, Fristenkalender, Liquiditätsvorschau, Dokumentenposteingang, SWIFT importieren, Importhistorie, Training & Erkennung.
- **ADMINISTRATION**, in drei Gruppen: *Stammdaten & Regeln* (Firmenstammdaten, Dokumentvorlagen, Rule Packs, Korrespondenzbanken, Freigabestufen, SLAs), *Zugriff & Schnittstellen* (Benutzer, API-Schlüssel, Webhooks, Audit-Protokoll), *Betrieb & Anbindung* (EBICS-Bankanbindung, Operationsmonitor, Mandant, Plattformadministration).
- Eine Gruppenüberschrift erscheint nur, wenn mindestens ein Eintrag der Gruppe für den Benutzer sichtbar ist (die Sichtbarkeit der Einträge bestimmen weiterhin Rechte und Profil). Ein neuer, unbekannter Eintrag bleibt im Arbeitsbereich.
