# Projektdokumentation

Stand: 25.10.2026 · Corporate LC Manager 0.7.0

Die Dokumentation beschreibt den Quellstand, mit dem sie ausgeliefert wird.
Die Info-Seite `/info.html` enthält Copyright, Lizenz, Projektlinks und das
versionierte Inventar der Java-Laufzeitbibliotheken.

## Überblick
- [Fachliche Dokumentation und Bedienabläufe](fachliche-dokumentation.md)
- [Technische Dokumentation und Betrieb](technische-dokumentation.md)
- [Erstinstallation: Leitfaden für Betrieb und erste Benutzer](erstinstallation.md)
- [Roadmap](../ROADMAP.md) · [Änderungsprotokoll](../CHANGELOG.md)
- [Einheitliche Bedienelemente](oberflaeche-einheitlich.md) · [Layout prüfen](layout-pruefung.md)

## Akten, Prüfung und Befunde
- [Prüfmodus, Aktenliste und Prüfbericht](pruefmodus.md)
- [Prüfungsassistent: geführte Prüfung in sieben Schritten](pruefassistent.md)
- [Befunde: Übersicht, kompakte Liste, Sammelbestätigung](befunde-uebersicht.md)
- [Sanktionsprüfung (Listen, Schiffe, Embargoländer, Vier-Augen)](sanktionspruefung.md)
- [HS-Code-Erkennung](hs-codes.md)
- [Erkannte Prüfdaten](erkannte-pruefdaten.md) · [Fakten-Matrix](fakten-matrix.md) · [Ergänzende Prüfdaten](supplementary-rule-facts.md)
- [Dokumentenvorlage und Prüfstufen](document-review-stages.md) · [Prüfablauf](document-review-workflow.md) · [Original/Kopie](document-original-copy.md)
- [Versionierte Prüfgrundlagen](versionierte-pruefregeln.md)
- [Fachliche LC-Bedingungen und Gebührenschätzungen](lc-bedingungen-und-gebuehren.md)
- [Avis der Bank einlesen](avis-einlesen.md) · [Einreichungsschreiben und Kundenstamm](einreichungsschreiben.md)
- [Corporate: Beschaffungsplan](corporate-beschaffungsplan.md) · [Packliste: CSV-Import](packliste-csv-import.md)
- [Teams und Gruppeninbox](gruppeninbox.md)
- [Korrespondenzbanken](korrespondenzbanken.md)
- [Garantien (Corporate)](garantien.md)
- [Domain Events und Webhooks](webhooks.md)
- [Externe REST-API und API-Schlüssel](rest-api.md)
- [Erwarteter Geldeingang in den Fristen](erwarteter-geldeingang.md)
- [Zahlungsstatus der Akte](zahlungsstatus.md)
- [Liquiditätsvorschau (Corporate)](liquiditaetsvorschau.md)
- [SWIFT-Ordner: Nachrichten empfangen (Bank)](swift-ordner.md)
## Posteingang, Erkennung und Training
- [Gemischte Sammel-PDFs aufteilen](inbox-pdf-splitting.md) · [Automatisches Aufteilen](inbox-auto-split.md)
- [Training der Dokumentart (inkl. Absender-Layouts)](dokumentart-training.md)
- [Bestätigte Aufteilungsmuster](split-training.md) · [Qualität des Aufteilungstrainings](split-training-quality.md)
- [Hintergrundverarbeitung im Posteingang](inbox-background-extraction.md) · [Erfassungszugang für Scan-Stationen](erfassungszugang.md)
- [Eingangskanäle: gemeinsamer Eingang, Ordner-Kanal (Scan to Folder) und E-Mail-Postfach](eingangskanaele.md)
- [Scan-Profil und OCR-Engine](scan-profile.md) · [OCR-Konfidenz](ocr-confidence.md) · [OCR-Verarbeitung](ocr-performance.md) · [OCR-Toleranz, Modelle, Formularfelder, Vergleich](ocr-toleranz-und-modelle.md)
- [Erkennungs-Benchmark](recognition-benchmark.md) · [KI-Unterstützung (optional)](ki-vorschlaege.md)

## Prüfregeln
- [Zielarchitektur lc-rule-engine](lc-rule-engine.md) · [Interne Rule Packs](internal-rule-packs.md)
- [Schema 2](rule-engine-v2.md) · [Schema 3](rule-engine-v3.md) · [Schema 4](rule-engine-v4.md) · [Schema 5](rule-engine-v5.md)

## Mandanten, Benutzer und Plattform
- [Benutzer & Rechte und Seite „Mandant"](benutzer-und-rechte.md)
- [Plattformadministration](platform-administration.md)
- [Mandanten anlegen und auswählen](tenant-activation.md) · [Lebenszyklus und Modulprofile](tenant-lifecycle.md)
- [Mitgliedschaften bereitstellen](tenant-membership-provisioning.md) · [Archivierung und Löschung](tenant-retention-and-deletion.md) · [Löschen von Testdaten](tenant-test-purge.md)
- [Mandanten- und Sprachmigration](tenant-i18n-migration.md)

## Sicherheit, Betrieb und Anbindungen
- [Anmeldesicherheit und Admin-2FA](anmeldesicherheit.md) · [Browser-Sicherheit und CSP](browser-sicherheit.md)
- [Upload-, Verarbeitungs- und Backup-Sicherheit](betriebssicherheit.md) · [Audit-Konzept](audit-concept.md)
- [EBICS-Anbindung](ebics-anbindung.md)
- [Regressionstests Dokumentablauf](document-workflow-regression.md)
