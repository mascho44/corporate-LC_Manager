# lc-rule-engine – Zielarchitektur und Roadmap

Status: versioniertes Framework im Manager vorhanden; noch kein eigenständiger Dienst.
Stand: 05.10.2026.
Bezug: [#29](https://github.com/mascho44/corporate-LC_Manager/issues/29)
und [#39](https://github.com/mascho44/corporate-LC_Manager/issues/39).

## Grundsatz und Quellen

Wir bauen keine Kopie von ISBP 821 in die Software. Rule Packs enthalten
abstrahierte Prüfregeln, Anwendungsbedingungen, Tests und Quellenreferenzen,
nicht den vollständigen ICC-Text. Lizenzierte Originalunterlagen und deren
vollständige OCR-Texte werden weder im öffentlichen Repository noch in
Docker-Images oder öffentlich abrufbaren Endpunkten verteilt.

Fachliche Grundlage sind UCP 600 und ISBP Publication 821 (2023).
ISBP beschreibt die Bankpraxis zur Anwendung von UCP 600 auf unterschiedliche
Dokumente; siehe [ICC-Publikationsbeschreibung](https://2go.iccwbo.org/explore-our-products/international-standard-banking-practice-isbp-3.html).
Die Bereitstellung der Unterlagen ersetzt keine fachliche Freigabe der
abgeleiteten Einzelregeln. Interne Vorprüfungen bleiben davon unterscheidbar.

UCP-Prüfungen setzen die Einbeziehung der Regeln in das Akkreditiv voraus.
Ausdrückliche Änderungen oder Ausschlüsse und die gültige LC-Fassung nach
Amendments sind zu berücksichtigen. Bei unbekannter Anwendbarkeit oder
unzureichender Datenqualität entsteht ein Prüfhinweis, keine automatische
Bestätigung der Konformität.

## Pipeline

```text
MT700 → LC Model → Dokumente/OCR → Normalized Facts
      → UCP/ISBP Rule Engine → Findings → Discrepancies → Human Review
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

Aktueller Implementierungsumfang: formatneutraler Engine-Vertrag,
Pack-Katalog unter `/api/rule-packs`, Anwendbarkeitsprüfung und eine
Rechnungswährungs-Teilprüfung mit Referenz auf UCP 600 Art. 18(a)(iii).
Ergebnisse bleiben Hinweise zur menschlichen Prüfung, auch bei übereinstimmender
Währung. Unbekannte Regelprofile, fehlende Angaben und ausdrückliche
Ausnahmeprofile werden nicht als erfolgreiche Prüfung bewertet.
Die fünf übrigen Packs sind als `PLANNED` ohne aktive Regeln ausgewiesen.
ISBP-Einzelregeln sind noch nicht fachlich freigegeben oder vollständig umgesetzt.
Das Framework ist nicht mit vollständiger ICC-Konformitätsprüfung gleichzusetzen.

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
UCP-/ISBP-Konformitätsprüfung darf aus einem Teilumfang nicht behauptet werden.
