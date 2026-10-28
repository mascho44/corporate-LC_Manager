# LC-Entwurf prüfen

Der Menüpunkt **LC-Entwurf prüfen** (unter „SWIFT importieren“; Recht „LC bearbeiten“, „Dokumente prüfen“ oder „SWIFT importieren“) prüft den **Entwurf eines Akkreditivs**, den eine dritte Partei vor der Eröffnung schickt, meist die eröffnende Bank. Gedacht ist er für den Begünstigten, der Fehler vor der Eröffnung korrigieren lassen möchte, statt später eine Änderung zu beantragen.

Eingabe: der MT700-Text (eingefügt) oder eine Datei (PDF oder Text; der Text wird wie beim SWIFT-Import gelesen). **Der Entwurf wird nicht gespeichert**; im Audit steht nur, dass geprüft wurde (`LC_DRAFT_CHECKED`).

## Was geprüft wird
Es gibt **keinen Vergleich mit einem Vertrag**; die Prüfung bezieht sich allein auf den Entwurf.

| Bereich | Befund |
|---|---|
| Pflichtangaben | fehlen :20:, :40A:, :31D:, :32B:, :50:, :59:, :45A:, :46A: oder :41a: (Abweichung); fehlt der späteste Verladetag (:44C:/:44D:) (Hinweis); fehlt die Vorlagefrist (:48:) (Information, es gelten 21 Tage) |
| Form | „REVOCABLE“ statt unwiderruflich (Abweichung) |
| Daten | Verfall oder Verladetag in der Vergangenheit, Verfall vor dem spätesten Verladetag, Ausstellung nach Verfall (Abweichungen); weniger Tage zwischen Verladetag und Verfall als die Vorlagefrist (Hinweis) |
| Betrag | nicht positiv (Abweichung) |
| Vorlagefrist | unter 10 Tagen (Hinweis) |
| Klauseln in :46A:/:47A: | Bedingung hängt vom Auftraggeber ab (Unterschrift, Freigabe, Zustimmung); unbestimmte Bedingung („to be advised“, „as per contract“); sehr kurze Frist („within 5 days“) (Hinweise); Legalisation oder Beglaubigung (Information) |
| Gebühren | Gebühren zu Lasten des Begünstigten in :71D: (Information) |

Jeder Befund nennt die Fundstelle (Feld, bei Klauseln den Wortlaut) und einen Hinweis, wie er sich beheben lässt. Texte in Deutsch und Englisch.

## Grenzen
Die Klauselprüfung ist eine **Textsuche** und erkennt nur typische Formulierungen; sie ersetzt keine fachliche Prüfung des Entwurfs und keine rechtliche Bewertung. Ein leeres Ergebnis heißt nur, dass diese Regeln nichts gefunden haben.

Schnittstelle: `POST /api/lc-draft-check` (JSON `{text}` oder Datei als `multipart`). Umsetzung: `LcDraftReviewService`, `lc-draft-check.js`.
