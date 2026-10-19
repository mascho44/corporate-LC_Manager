# Einreichungspaket für die Bank

In der Akte erzeugt **Einreichungspaket** (neben „Dokumentensatz erzeugen“, Recht „Dokumente erstellen“) die Unterlagen, mit denen Dokumente unter einem Akkreditiv bei der Bank vorgelegt werden: ein **Begleitschreiben** und die **Dokumente** selbst in der üblichen Reihenfolge, als ZIP.

## Ablauf

1. **Vorschlag:** Der Dialog listet alle Dokumente der Akte, die der Bank vorgelegt werden (Rechnung, Packliste, Transportdokumente, Versicherung, Ursprungszeugnis, Prüf- und Qualitätszertifikate, Begünstigtenbescheinigung, Tratte u. a.), in der Reihenfolge einer Einreichung. Nicht dazu gehören das SWIFT-Dokument, das Avisierungsschreiben und die Anlage des Akkreditivs. Je Dokumentart ist das erste Dokument gewählt, mit der **Zahl der Originale und Kopien, die das Akkreditiv verlangt** („3 Originale“, „2 Kopien“, „Full Set 3/3“); weitere Dokumente derselben Art sind abgewählt.
2. **Hinweise:** Die **offenen Abweichungen der Vorprüfung** stehen oben im Dialog (zum Beispiel ein gefordertes, aber fehlendes Dokument oder ein zu hoher Rechnungsbetrag). Sie sperren das Paket nicht, sollten aber vor der Einreichung geklärt sein.
3. **Eingabe:** Datum, Empfänger (die Bank; vorbelegt mit „benutzbar bei“, sonst avisierende, sonst eröffnende Bank, „ANY BANK“ wird übersprungen), eigene Referenz, Bemerkungen und je Dokument die Zahl der Originale und Kopien.
4. **Paket:** Der Download enthält `00-Einreichungsschreiben.docx` und die gewählten Dokumente als `01-…`, `02-…` in der Reihenfolge der Liste. Das interne Prüfprotokoll bleibt draußen.
5. Wahlweise wird das Datum als **Vorlagedatum** in der Akte gespeichert (wie unter „Vorlagedatum“ beschrieben).

## Das Begleitschreiben

Englisch, im Briefkopf der Firma (siehe `word-vorlagen.md`): Empfänger, Datum, Referenzen, Betreff „Presentation of documents under Documentary Credit No. …“, ein einleitender Satz mit Bank, Auftraggeber, Betrag, letztem Verladedatum und Verfall (nur mit den Angaben, die vorhanden sind), die Liste der Dokumente (Nummer, Art, Dokumentnummer und -datum, Originale, Kopien, Seiten bei PDF), Bemerkungen, die Bitte um Prüfung und Gutschrift auf das Konto der Firma, Grußformel und Unterschriftsblock. Fehlt ein Angabe der Firma (Anschrift, Telefon, USt-ID, Bank), bleibt die Zeile weg statt mit „-“ zu erscheinen.

## Weitere Änderungen

- Die Briefköpfe und Fußzeilen aller erzeugten Dokumente werden aus den vorhandenen Firmenangaben zusammengesetzt; fehlende Angaben hinterlassen keine Striche mehr. Neue Platzhalter: `{{companyAddressLine}}`, `{{companyContactLine}}`, `{{companyLegalLine}}`, `{{companyBankLine}}`, `{{placeDate}}`, `{{paymentInstruction}}` (die einzelnen Firmen-Platzhalter bleiben für eigene Vorlagen).
- Die Erkennung der Originalanzahl liest auch „FULL SET 3/3 CLEAN ON BOARD …“ (bisher nur „3/3 ORIGINAL“).

Schnittstelle: `GET /api/lcs/{id}/submission-package/plan`, `POST /api/lcs/{id}/submission-package` (ZIP; Recht „Dokumente erstellen“).
