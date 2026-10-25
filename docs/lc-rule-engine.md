# lc-rule-engine – Zielarchitektur und Roadmap

Status: Zielbild einer eigenständigen Regel-Engine; zurückgestellt. Das interne
versionierte Prüf- und Audit-Framework (eigene Rule Packs) bleibt im Einsatz.
Stand: 25.10.2026.

## Grundsatz

Rule Packs enthalten abstrahierte Prüfregeln, Anwendungsbedingungen, Tests und
Quellenreferenzen. Eigene Rule Packs können importiert und aktiviert werden
(siehe [interne Rule Packs](internal-rule-packs.md)). Interne Vorprüfungen
bleiben von fachlich freigegebenen Einzelregeln unterscheidbar.

Regelwerkprüfungen setzen die Einbeziehung des Regelwerks in das Akkreditiv
voraus. Ausdrückliche Änderungen oder Ausschlüsse und die gültige LC-Fassung
nach Amendments sind zu berücksichtigen. Bei unbekannter Anwendbarkeit oder
unzureichender Datenqualität entsteht ein Prüfhinweis, keine automatische
Bestätigung der Konformität.

- Regeln werden als eigenständige Datenbedingungen und Vergleichsoperationen
  beschrieben.
- Tests verwenden selbst erstellte, synthetische Fälle.
- Quellenreferenzen bleiben knappe bibliografische Angaben mit Artikel- oder
  Absatznummer.
- Originale und vollständige OCR-Texte bleiben privat und werden nicht für
  Modelltraining, öffentliche Trainingsdaten oder öffentliche Suchindizes genutzt.

## Pipeline

```text
MT700 → LC Model → Dokumente/OCR → Normalized Facts
      → Regel-Engine → Findings → Discrepancies → Human Review
```

Der vorhandene MT700-Parser und OCR/Document Classifier werden über Adapter
angeschlossen. Die Pipeline ist das Zielbild, nicht die Behauptung einer
bereits vollständig implementierten Verarbeitung.

## Verantwortungsgrenzen

Der Corporate LC Manager verwaltet Akten, Originaldokumente, Benutzerrechte,
OCR/Training, Amendments, fachliche Entscheidungen und deren Audit-Protokoll.
Er übergibt eine unveränderliche LC-Fassung und normalisierte Dokumentfakten
an `lc-rule-engine`. Die Engine benötigt keine SWIFT-Feldnummern als
fachliches Datenmodell, keine Benutzerverwaltung und keinen direkten Zugriff
auf die Datenbank des Managers.

Der Dienst bewertet Fakten anhand ausgewählter, versionierter Rule Packs.
Er erstellt Findings, aber trifft keine abschließende Freigabeentscheidung.
Erkannte mögliche Abweichungen werden im Manager als Discrepancies fachlich
bewertet. Human Review bleibt erforderlich; ein Engine-Ausfall oder eine
unvollständige Prüfung darf niemals als erfolgreiche Prüfung erscheinen.

## Phasen

Die folgenden Phasen sind vollständig zurückgestellt. Bestehende explizite
LC-Bedingungsvergleiche sind interne Vorprüfungen ohne Zuordnung zu einer
Einzelregel. Historische Prüfbelege bleiben erhalten, werden aber nicht als
aktive Regeln erneut ausgeführt.

1. Engine-Grundlage: versionierter Vertrag für LC Model, Normalized Facts und
   Findings; nachvollziehbare Quellenzuordnung, Anwendbarkeit und fachliche
   Freigabe je Regel. Bestehende interne Prüfungen getrennt kennzeichnen.
2. Dokumenttypspezifische Rule Packs auf derselben Engine:
   `commercial-invoice`, `bill-of-lading`, `air-waybill`,
   `insurance-document`, `packing-list`, `certificate-of-origin`.
   Gemeinsame Regeln und dokumentübergreifende Vergleiche werden zentral
   gepflegt, nicht pro Pack dupliziert.
3. End-to-End-Integration des vorhandenen MT700-Parsers und der
   OCR/Klassifikation, Normalisierung sowie Findings-/Review-Oberfläche.
   Vergleich mit bisherigen Prüfungen vor Aktivierung im Produktivbetrieb.
4. Eigenständiger Docker-Dienst mit geschützter interner Schnittstelle,
   Health/Readiness, Timeouts, begrenzten Eingaben und beobachtbarem Betrieb.
   Protokoll, Authentifizierung und gegebenenfalls Queue-Anbindung werden
   vor der Implementierung konkretisiert; keine öffentliche anonyme API.

## Datenvertrag und Nachvollziehbarkeit

- Normalized Facts: Wert, Datentyp, Einheit/Währung, Dokument-ID, Seite und
  Fundstelle, Extraktionskonfidenz und Herkunft einschließlich Korrekturen.
- Prüfauftrag: LC-Fassung, Dokument-/Faktenstand, Schema-Version,
  explizite Rule-Pack-Versionen und Prüflauf-ID.
- Rule Pack: Pack-ID, Version, unterstützter Dokumenttyp, Regel-IDs,
  Quellenedition sowie Artikel-/Absatzreferenzen und Freigabestatus.
- Finding: Regel-/Pack-Version, Quellenreferenz, betroffene LC-Bedingung,
  Dokumentbeleg, Schweregrad und Zustand einschließlich „nicht prüfbar“.
- Audit: Eingabefingerprint, Engine-Version, ausgewählte Packs und Ergebnis.
  Reviews sind an genau diesen Befundstand gebunden; geänderte Eingaben
  dürfen frühere Entscheidungen nicht unbemerkt übernehmen.

## Abnahmekriterien

Für jede aktivierte Regel sind fachlich geprüfte Positiv-, Negativ- und
Grenzfälle sowie Anwendbarkeits- und Unsicherheitsfälle erforderlich.
Identische Eingaben und Versionen liefern identische fachliche Ergebnisse.
Fehlende Daten, OCR-Unsicherheit, LC-Ausnahmen und Dienstfehler sind sichtbar.
Einzelregeln werden erst nach fachlicher Freigabe aktiviert. Eine vollständige
Konformitätsprüfung darf aus einem Teilumfang nicht behauptet werden.
