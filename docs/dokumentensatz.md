# Dokumentensatz erzeugen

In einer Akte erzeugt **Dokumentensatz erzeugen** (neben „Dokument hochladen“, Recht „Dokumente erstellen“) alle Dokumente einer Lieferung in einem Schritt: Handelsrechnung, Packliste und die Zertifikate, die das Akkreditiv verlangt. Nummern, Datum, Positionen, Summen und Gewichte sind in allen Dokumenten gleich; die Word-Vorlagen (siehe `word-vorlagen.md`) liefern das Layout.

## Ablauf

1. **Vorschlag:** Der Dialog schlägt vor, was das Akkreditiv hergibt: die verlangten Dokumente (aus den geforderten Dokumenten, Feld 46A; ohne Treffer Rechnung und Packliste), eine Rechnungsnummer (`INV-<Jahr>-<laufende Nummer>`), das heutige Datum und eine Position aus der Warenbeschreibung mit Menge, Einheit und dem Akkreditivbetrag. Zu jedem Dokument steht, ob es im Akkreditiv verlangt ist, wie viele Originale und ob eine Unterschrift gefordert sind.
2. **Eingabe:** Rechnungsnummer, Datum, Positionen (Beschreibung, Menge, Einheit, Einzelpreis, Betrag, Packstücke, Netto- und Bruttogewicht), Hinweise und Markierungen. Ein Betrag ohne Eingabe ergibt sich aus Menge mal Einzelpreis. Unter den Positionen zeigt der Dialog sofort, ob die Summe über dem zulässigen Höchstbetrag liegt (Akkreditivbetrag plus Toleranz aus :39A:).
3. **Erzeugen:** Je gewähltem Dokument entsteht eine Word-Datei in der Akte. Die Nummern der übrigen Dokumente leiten sich von der Rechnungsnummer ab (`INV-2026-0815` → `PL-2026-0815`, `COO-2026-0815`, `BC-…`, `QC-…`). Packstücke und Gewichte der Packliste ergeben sich aus den Positionen, wenn sie nicht gesondert angegeben sind.
4. **Ergebnis:** Der Dialog listet die erzeugten Dokumente mit Download, **Hinweise** (Summe über dem Höchstbetrag, Datum nach Verfall des Akkreditivs, fehlende Packstücke oder Beträge) und die **Befunde der automatischen Prüfung** an den neuen Dokumenten. Hinweise zu Originalen, Unterschrift und Aussteller gehören zur manuellen Prüfung und bleiben stehen.

## Korrekturen am Erzeugen

- Der Betrag einer erzeugten Rechnung ist jetzt die **Summe der Positionen** (bisher der Akkreditivbetrag). Die Betragsprüfung schlägt damit auch bei erzeugten Rechnungen an.
- Der für die Prüfung gespeicherte Text enthält wie das Dokument Incoterm, Häfen, Zahlungsbedingung, Erklärung und Unterschriftsblock. Dadurch entfallen die Hinweise „Incoterm nicht gefunden“ und „Sprache nicht prüfbar“.
- Zertifikate zeigen in der Positionstabelle nur Position, Beschreibung, Menge und Einheit (keine Preisspalten).

Schnittstelle: `GET /api/lcs/{id}/document-set/proposal`, `POST /api/lcs/{id}/document-set` (Recht „Dokumente erstellen“).

## Word oder PDF

Im Dialog „Format“ wählen: **Word** (bearbeitbar, nutzt die Mustervorlage der Firma) oder **PDF** (fertig zum Versand, immer das Standardlayout; eigene Word-Vorlagen gelten nur für Word). Das PDF hat dieselben Abschnitte wie die Word-Vorlage. Dasselbe gilt für das Begleitschreiben im Einreichungspaket (`letterFormat`); im ZIP heißt es dann `00-Einreichungsschreiben.pdf`. Schnittstelle: Feld `format` (`docx`|`pdf`) beim Dokumentensatz.
