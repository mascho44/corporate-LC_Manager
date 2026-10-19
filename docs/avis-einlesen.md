# Avisierungsschreiben und Akkreditivtext einlesen

Ein Avis der Bank besteht aus einem Begleitschreiben und dem Text des Akkreditivs. Im Dokumenteneingang legt die App daraus eine neue Akte an (Aktion „Als Akkreditiv einlesen“). Der Akkreditivtext kann in vier Darstellungen stehen:

1. **SWIFT mit Doppelpunkt:** `:20:LC-1`, `:32B:EUR125000,00`.
2. **Tabelle mit Feldnummer:** `20  Documentary Credit Number` mit dem Wert darunter, auch mit Texterkennungsfehlern in der Nummer.
3. **Mit Feldnamen ohne Feldnummer (neu):** Ausdrucke aus Bankportalen und Handelsfinanzsystemen, auf Englisch oder Deutsch, zum Beispiel „Documentary Credit Number: LC-1“, „Akkreditivnummer   LC-1“, „Betrag: EUR 125.000,00“. Der Name darf am Zeilenanfang stehen, mit Doppelpunkt oder zwei Leerzeichen dahinter, oder allein in der Zeile mit dem Wert in den folgenden Zeilen.
4. **Nur das Begleitschreiben:** Dann liest die App Referenz, Betrag, Ablauf, Banken und Auftraggeber aus dem Schreiben selbst und schlägt sie vor.

## Was bei Ausdrucken ohne Feldnummer erkannt wird

Akkreditivnummer, Ausstellungs- und Verfallsdatum mit Ort, Betrag mit Währung, Auftraggeber, Begünstigter, eröffnende und avisierende Bank, Teilverladungen, Umladung, Verlade- und Entladehafen, spätester Verladetag, Warenbeschreibung, geforderte Dokumente, zusätzliche Bedingungen, Vorlagefrist, Bestätigung, Art des Akkreditivs und Regelwerk. Englische und deutsche Bezeichnungen sind hinterlegt (`LabelledCreditNormalizer`).

Daten (`05.10.2026`, `5. Oktober 2026`, `October 5, 2026`, `2026-10-05`, `05/10/26`) und Beträge (`EUR 125.000,00`, `125,000.50 USD`, `CHF 1'250'000`) werden in die MT700-Form umgewandelt. Zahlenangaben mit Punkt und Komma, Leerzeichen oder Apostroph als Tausendertrenner sind erlaubt; Datumsangaben mit Schrägstrich lesen wir tagesvoran.

## Sicherungen

- Der Text zählt nur als Akkreditiv, wenn Akkreditivnummer und Betrag und mindestens zwei weitere Felder gefunden werden. Ein Begleitschreiben mit „Referenz“ und „Betrag“ wird nicht fälschlich als Akkreditiv gelesen.
- Kommt eine Bezeichnung ein zweites Mal vor (zum Beispiel in einer Zusammenfassung), gilt die erste Lesung.
- Seitenköpfe und -fußer („Seite 2 von 3“, „Page 2 of 3“) werden entfernt, auch mitten in einem Feld.
- Das Begleitschreiben vor dem Akkreditivtext endet mit einer Grußformel. Der Text wird deshalb auch zwischen den Grußformeln gelesen; es gewinnt die Lesung mit den meisten Feldern, sodass die Anlage hinter dem Schreiben nicht abgeschnitten wird.
- Alles wird vor dem Anlegen wie ein MT700-Import geprüft und in der Vorschau angezeigt.

## Grenzen

Zwei getrennte Dateien (Schreiben und Anlage) werden noch jeweils für sich eingelesen. Die Beispiele zum Testen liegen unter `src/test/resources/avis/` (alle Firmen und Zahlen sind erfunden).
