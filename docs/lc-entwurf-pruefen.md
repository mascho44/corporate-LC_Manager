# LC-Entwurf prüfen

Der Menüpunkt **LC-Entwurf prüfen** (unter „SWIFT importieren“; Recht „LC bearbeiten“, „Dokumente prüfen“ oder „SWIFT importieren“) prüft den **Entwurf eines Akkreditivs**, den eine dritte Partei vor der Eröffnung schickt, meist die eröffnende Bank. Gedacht ist er für den Begünstigten, der Fehler vor der Eröffnung korrigieren lassen möchte, statt später eine Änderung zu beantragen.

Eingabe: das PDF des Entwurfs; die Prüfung startet sofort nach der Auswahl, der Text wird wie beim SWIFT-Import gelesen. Ersatzweise lässt sich der MT700-Text einfügen („Stattdessen Text einfügen“). Das Ergebnis sieht aus wie die Dokumentenprüfung: oben die gelesenen Eckdaten (Referenz, Betrag, Verfall, Verladung) und Zähler für Abweichungen, Hinweise und erfüllte Punkte, darunter eine Zeile je Prüfpunkt mit Haken (erfüllt), Ausrufezeichen (Hinweis), Kreuz (Abweichung) oder „i“ (Information). **Der Entwurf wird nicht gespeichert**; im Audit steht nur, dass geprüft wurde (`LC_DRAFT_CHECKED`).

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

## Ablauf in der Akte (Bank)
In Bank-Mandanten zeigt die Übersicht einer Akte oben die Karte **Ablauf der Prüfung** mit vier Schritten und ihrem Stand:

1. **Entwurf prüfen (optional):** Mit „Entwurf hinterlegen“ (Recht „LC bearbeiten“) wird der Entwurf als PDF gewählt, sein Text gelesen und zur Akte gespeichert (das PDF braucht durchsuchbaren Text; ersatzweise lässt sich der Text einfügen); angezeigt werden Abweichungen und Hinweise der Entwurfsprüfung. „Ersetzen“ und „Entfernen“ gibt es ebenfalls. Ohne Entwurf gilt der Schritt als übersprungen.
2. **LC mit Entwurf abgleichen:** Verglichen werden die Felder Form, Verfall, Betrag, Toleranz, benutzbar bei, Teil-/Umladung, Verladeorte und -termine, Waren, Dokumente (:46A:), Zusatzbedingungen (:47A:), Vorlagefrist, Bestätigung, Parteien und Gebühren, ohne Beachtung von Groß-/Kleinschreibung und Leerraum. „Details anzeigen“ zeigt je abweichendem Feld den Entwurf neben der LC. Die Referenz (:20:) wird nicht verglichen. Ohne Entwurf entfällt der Schritt.
3. **LC prüfen:** Dieselbe Prüfung wie beim Entwurf, angewendet auf den SWIFT-Text der Akte (Pflichtangaben, Fristen, riskante Klauseln). Fehlt der SWIFT-Text (zum Beispiel bei von Hand angelegten Akten), ist der Schritt nicht prüfbar.
4. **Dokumente prüfen:** Folgt, sobald Dokumente eingereicht sind; „Prüfungsassistent öffnen“ startet die geführte Dokumentenprüfung. Ist die Prüfung abgeschlossen ([Prüfabschluss](pruefmodus.md)), steht der Schritt auf erledigt.

Der Stand wird aus dem hinterlegten Entwurf und der LC berechnet; gespeichert wird nur der Entwurf (Tabelle `lc_draft`, V156; Audit `LC_DRAFT_SAVED`, `LC_DRAFT_REMOVED`). Schnittstellen: `GET /api/lcs/{id}/flow`, `PUT` und `DELETE /api/lcs/{id}/draft`.
