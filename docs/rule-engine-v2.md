# Prüf-Engine Schema 2: Handelsrechnungen

Weitere generische Bausteine und der aktuelle Funktionsumfang stehen unter [Schema 3](rule-engine-v3.md). Seit Schema 3 beträgt das Upload-Limit für ergänzende Prüfdaten 64 KiB.

Schema 2 erweitert die deklarative Engine, ohne hochgeladenen Code auszuführen. Schema-1-Packs bleiben gültig und behalten ihre bisherige kanonische Darstellung. Das Beispiel `/rule-pack-example-v2.json` enthält ausschließlich eigene synthetische Funktionstests, keine ICC-Regeln.

## Prüfdaten erfassen

In der LC-Dokumentenakte öffnet **Prüfdaten** die strukturierten Angaben zum Dokument. **LC-Prüfkontext** führt zum gemeinsamen Kontext der Akte.

- Dokument: Aussteller, Empfänger und Warenbeschreibung.
- LC: anzuwendender Regelstandard (`UCP600` oder `OTHER`), tatsächlich übertragenes LC (`true` oder `false`), zweiter Begünstigter und gültige Warenbeschreibung.
- Antragsteller und Begünstigter stammen aus den bestehenden LC-Stammdaten.

Leere Angaben sind unbekannt. Übertragbarkeit und tatsächlich erfolgte Übertragung sind nicht dasselbe. Die Anwendung leitet den Übertragungsstatus nicht automatisch aus SWIFT-Text oder OCR ab. Angaben müssen fachlich geprüft werden.

Dokument-Prüfdaten benötigen `DOCUMENT_UPLOAD`; LC-Prüfkontext benötigt `LC_EDIT`. Lesen setzt eine Anmeldung voraus, entsprechend dem bestehenden gemeinsamen Aktenzugriff. Änderungen setzen die aktiven Prüfentscheidungen dieser LC-Akte zurück. Speicherung, Rücksetzung und Audit-Eintrag laufen in derselben Transaktion. Audit-Einträge enthalten Benutzer, Objekt, geänderte Feldnamen und Prüfsummen des vorherigen/neuen Datenstands; sie kopieren nicht den vollständigen Dokumentinhalt ins Protokoll.

## Regeln

Schema 2 ergänzt:

- `mode`: `AUTOMATIC` (Standard) oder `MANUAL`.
- `conditions`: höchstens acht Feldvergleiche mit `EQ` oder `NE`; alle müssen erfüllt sein.
- `facts` in Testfällen: explizite Werte für Anwendungsbedingungen.

Zusätzliche Vergleichspaare:

- `DOCUMENT_ISSUER` gegen `LC_BENEFICIARY` oder `LC_SECOND_BENEFICIARY`.
- `DOCUMENT_RECIPIENT` gegen `LC_APPLICANT`.
- `DOCUMENT_GOODS_DESCRIPTION` gegen `LC_GOODS_DESCRIPTION`, zwingend mit `MANUAL`.

Automatische Parteienvergleiche normalisieren Groß-/Kleinschreibung und Leerraum, aber führen keine semantische Identitätsprüfung durch. Abweichungen, Namensänderungen und rechtliche Identität benötigen fachliche Beurteilung; das Demo verwendet deshalb Warnungen.

Eine Regel kann beispielsweise ausdrücklich einen geprüften Regelstandard oder Übertragungsstatus voraussetzen. Ein Pack ohne passende Bedingungen ist weiterhin global anwendbar. Der Betreiber muss die Anwendungsbedingungen seiner Packs korrekt definieren.

## Ergebnisse und Testpflicht

- `PASS`: der konkrete automatische Vergleich ist erfüllt; keine Bestätigung des gesamten Dokuments.
- `FAIL`: der konkrete Vergleich ist verletzt.
- `NOT_APPLICABLE`: mindestens eine Anwendungsbedingung ist sicher nicht erfüllt; wird nicht als grünes Ergebnis ausgegeben.
- `NOT_EVALUABLE`: erforderliche Anwendungs-/Vergleichsdaten fehlen oder sind ungültig.
- `MANUAL_REVIEW`: Anwendungsbedingungen und benötigte Texte sind vorhanden; eine fachliche Prüfung bleibt offen. Selbst gleiche Warenbeschreibungen werden nicht automatisch bestätigt.

Automatische Regeln benötigen Tests für PASS, FAIL und NOT_EVALUABLE. Manuelle Regeln benötigen MANUAL_REVIEW und NOT_EVALUABLE. Bedingte Regeln benötigen zusätzlich NOT_APPLICABLE und einen Fall mit unklarer Anwendbarkeit. Aktivierung setzt alle tatsächlich bestandenen Tests und die ausdrückliche Rechtebestätigung voraus.

Regelversion, Pack-Prüfsumme, Ergebnis und ausgewertete Prüfdaten werden Bestandteil der Befundgrundlage. Änderungen führen daher zu einem anderen Prüfbezug. Alte Bestätigungen werden nicht stillschweigend übernommen.

## API und Datenbank

- `GET/PUT /api/lcs/{lcId}/rule-facts`
- `GET/PUT /api/lcs/{lcId}/documents/{id}/rule-facts`

PUT ersetzt den jeweiligen ergänzenden Prüfdatenbestand mit einer JSON-Map aus zugelassenen Feldnamen und Zeichenketten. Fehlende/leere Werte löschen die entsprechende Ergänzung. Fremde Aktenzuordnungen sowie unbekannte oder nicht zum Objekt passende Felder werden zurückgewiesen; CSRF bleibt erforderlich.

V45 ergänzt `rule_facts_json` in LC- und Dokumenttabelle. Für Parteien gelten 500 Zeichen, für Warenbeschreibungen 4.000 Zeichen. Prüfdaten-Uploads sind auf 32 KiB begrenzt; doppelte JSON-Schlüssel, unbekannte Felder und nicht-textuelle Werte werden zurückgewiesen. Schema, Ausdrücke, Felder und Operatoren bleiben begrenzt.

## Noch nicht umgesetzt

Dies ist keine vollständige UCP-/ISBP-Engine. Noch offen sind dokumentübergreifende Waren-/Mengenvergleiche, Vorlage- und Bankarbeitstage, dynamische Toleranzen, Rechnungs- versus Beanspruchungsbetrag, Original-/Unterschriftsstatus, Transport- und Versicherungsprofile sowie ein vollständiger privater Regelkatalog. Die Engine enthält keine ICC-Regeltexte, Quellen-PDFs oder privaten Packs.
