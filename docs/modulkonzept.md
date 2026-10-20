# Modulkonzept und Lizenzschlüssel (Entwurf zur Abstimmung)

Status: Vorschlag, noch nichts umgesetzt. Ziel: Der Plattform-Administrator schaltet Funktionen pro Mandant per Klick frei (Monetarisierung), und eigene Installationen werden über einen Lizenzschlüssel freigeschaltet.

## Ausgangslage

Pro Mandant gibt es heute zwei Schalter, `bankEnabled` und `corporateEnabled`. Die Durchsetzung auf dem Server liegt in `TenantModulePolicy` (Pfad und Methode gegen die Schalter), die Oberfläche liest sie als `LcWorkspaceModules`. Das Modulkonzept verallgemeinert diesen Mechanismus: statt zweier Schalter ein Katalog von Modulen, und Bank und Corporate werden zu Vorlagen (Bündeln).

## Module und Zuordnung

| Schlüssel | Modul | Gehört dazu (Schnittstellen und Oberfläche) | Voraussetzung |
|---|---|---|---|
| `core` | Basis (immer an) | Akten (`/api/lcs`, Bedingungen, Zeitachse, Aufgaben, Team), Dokumente hochladen und ansehen (`/api/documents`, Dossier), Fristen und Kalender (`/api/calendar`, Fristenmonitor), Benutzer, Rollen, Einladungen, Profil, Audit (`/api/audit`), Kennzahlen, Akte kopieren, Gesundheit und Sicherheit | – |
| `docgen` | Dokumentenerstellung | `/api/lcs/{id}/generated-documents`, `document-set`, `document-drafts`, `/api/document-templates`, `/api/settings/document-layouts`, `/api/company-profile`, `/api/companies`, Word- und PDF-Ausgabe | `core` |
| `submission` | Einreichung | `/api/lcs/{id}/submission-package`, Begleitschreiben, Einreichungsschreiben, Vorlagefrist | `core` |
| `check` | Prüfung | `/api/lcs/{id}/document-checks` (ohne Bank-Teile), Korrekturvorschläge, Schnellprüfung, MT707-Auswirkung (`/amendments`), Befund-Belege, Vorprüfung, Dokumentvergleich, `/api/check-rules` | `core` |
| `bankreview` | Bank-Prüfung | Prüfungsassistent und Prüfschritte (`review-steps`), dokumentweise Prüfung, Unstimmigkeitsmeldung, Regelquelle und Simulation, Regelpakete (`/api/settings/rule-packs`), Genehmigungsschwellen, Rule-Facts | `check` |
| `intake` | Eingang und Erfassung | Postfach (`/api/inbox`), Avis-Einlesen und neue Akte aus Avis, Erfassungs-Links (`/api/capture-tokens`, Upload), Eingangskanäle (`/api/intake`), Scan-Profil, OCR-Hilfen | `core` |
| `training` | Training und Erkennen | `/api/training/**`, Dokumentart-Training, gelernte Feldbezeichnungen, Aufteilungs-Training (derzeit ausgeblendet) | `intake` |
| `procurement` | Beschaffungsplan | `LcProcurementController`, Erinnerungen und Digest, Zieldaten im Kalender, Workflows | `core` |
| `integration` | Anbindungen | SWIFT-Import (`/api/imports`, `/api/lcs/import`), EBICS (`/api/ebics`), E-Mail-Versand aus Akten (`/api/lcs/{id}/emails`), Nachrichtenausgang (`/api/admin/outbox`) | `core` |
| `ai` | KI-Hinweise | `/api/ai`, KI-Hinweise in der Akte | `check` |
| `analytics` | Auswertung (später) | erweiterte Kennzahlen, Export, Audit-Suche und Hash-Kette | `core` |

Plattform-Funktionen (Mandanten, Plattform-Benutzer, Sanktionslisten, Monitoring, Heimatmandant) sind keine Module, sondern gehören dem Plattform-Administrator.

## Vorlagen (Pakete)

- **Corporate:** `core`, `docgen`, `submission`, `check`, `procurement`, `intake`
- **Bank:** `core`, `check`, `bankreview`, `intake`, `training`, `integration`
- Beliebige weitere Pakete ("Starter", "Professional") sind Konfiguration: eine Liste von Modul-Schlüsseln.

Beim Anwenden einer Vorlage werden die Module des Mandanten gesetzt und danach einzeln änderbar. Die heutigen Schalter `bankEnabled` und `corporateEnabled` werden bei der Migration in diese Vorlagen übersetzt.

## Technischer Entwurf

1. **Katalog im Code:** Enum `Module` mit Schlüssel, Namen, Beschreibung, Voraussetzungen und den Pfadmustern (ersetzt die Pfadlisten in `TenantModulePolicy`).
2. **Speicherung:** Tabelle `tenant_module` (Mandant, Modul, freigeschaltet seit, gültig bis (optional), Notiz). Zeilenebenen-Sicherheit wie bei den anderen Mandantentabellen, Änderungen nur durch den Plattform-Administrator und im Audit-Protokoll.
3. **Durchsetzung:** Zentral in einem Filter anhand des Katalogs (Pfad und Methode gegen die Module des aktuellen Mandanten), Antwort 403 mit Hinweis "Modul nicht freigeschaltet". Ein Test stellt sicher, dass jede Schnittstelle genau einem Modul oder `core`/Plattform zugeordnet ist (wie `EndpointAuthorizationCoverageTest` für die Rechte).
4. **Oberfläche:** Die Modulliste kommt beim Anmelden (wie `LcWorkspaceModules`). Menüpunkte und Knöpfe erscheinen nur bei aktivem Modul. Wer ein gesperrtes Modul sieht, bekommt optional einen Hinweis "Im Paket nicht enthalten".
5. **Plattform-Oberfläche:** In der Mandantenverwaltung eine Modulliste mit Schaltern, Vorlagen-Auswahl und optionalem Ablaufdatum.
6. **Hintergrunddienste:** Erinnerungs- und Digest-Dienste prüfen das Modul des Mandanten (heute `isCorporateEnabled`).

## Lizenzschlüssel für eigene Installationen

Bei Betrieb beim Kunden gibt es keinen gemeinsamen Plattform-Administrator des Anbieters; der Schlüssel begrenzt, was dort freigeschaltet werden kann.

- **Format:** Signierter Text (Ed25519) mit Kunde, Installations-Kennung (optional), Modulliste, Höchstzahl Mandanten und Benutzer (optional), Gültig ab und bis. Der öffentliche Schlüssel ist in der Anwendung eingebaut, der private bleibt beim Anbieter. Prüfung vollständig lokal, ohne Verbindung nach außen.
- **Wirkung:** Wirksame Module eines Mandanten = vom Administrator freigeschaltet **und** durch die Lizenz gedeckt. Ohne Lizenz gilt ein Basispaket (`core`).
- **Eingabe:** Der Plattform-Administrator der Installation lädt den Schlüssel in der Plattform-Oberfläche hoch (Anzeige: Kunde, Module, Ablauf). Änderung wird im Audit-Protokoll vermerkt.
- **Ablauf:** Vor Ablauf Warnung (zum Beispiel 30 Tage), danach Karenzzeit (zum Beispiel 14 Tage) und dann nur noch lesender Zugriff auf Akten; nichts wird gelöscht.
- **Grenzen:** Ein rein lokal geprüfter Schlüssel ist keine technische Sperre gegen Manipulation des Quelltexts; er regelt die vertragliche Freischaltung. Für härteren Schutz wären eine Bindung an die Installation und eine regelmäßige Online-Prüfung nötig, was bei abgeschotteten Installationen oft nicht gewünscht ist.
- **Schlüsselverwaltung beim Anbieter:** Ein kleines Werkzeug (Kommandozeile) erzeugt und signiert Schlüssel; der private Schlüssel wird nicht im Repository gehalten.

## Umsetzung in Schritten

1. Katalog, Tabelle, Migration der heutigen Schalter, zentrale Durchsetzung und Abdeckungstest (ohne neue Oberfläche, Verhalten unverändert).
2. Modulliste in der Anmeldung, Menü und Knöpfe nach Modul, Plattform-Oberfläche mit Schaltern und Vorlagen.
3. Lizenzschlüssel: Signaturprüfung, Hochladen, Anzeige, Ablaufverhalten, Werkzeug zum Erzeugen.
4. Optional: Gültig-bis pro Modul, Hinweise "nicht im Paket", Nutzungszähler für verbrauchsabhängige Preise.

## Offene Entscheidungen

- Schnitt der Module und Namen (Tabelle oben): gefällt die Gruppierung, oder sollen Module zusammengelegt werden (zum Beispiel `docgen` und `submission`)?
- Ob `training` als eigenes Modul bleibt oder zu `intake` gehört (die Oberfläche blendet es derzeit aus).
- Ob die Lizenz auch Mandanten- und Benutzerzahl begrenzen soll.
- Verhalten nach Lizenzablauf (Karenzzeit, nur lesend).
