# Avis der Bank einlesen und LC-Akte anlegen

Ein Avis besteht aus dem **Anschreiben der Bank** und dem **Akkreditivtext**. Im Posteingang gibt es bei PDFs den Knopf **Als Avis einlesen …**.

1. Der Text der PDF (Texterkennung ist abgeschlossen) wird auf die gedruckten SWIFT-Felder des Akkreditivs durchsucht: Felder in der Form
   `:20:…`, `:32B:…` oder als Tabelle (`20 Documentary Credit Number …`). Das Anschreiben davor und die Grußformel danach (z. B. „Mit freundlichen Grüßen",
   „Best regards") werden ignoriert.
2. Die Vorschau zeigt Referenz, Auftraggeber, Begünstigten, Betrag, Ablauf und die geforderten Dokumente. Es gelten dieselben Prüfungen wie beim
   SWIFT-Import (Pflichtfelder :31D: und :32B:, Referenz noch nicht vorhanden). Fehler sperren das Anlegen.
3. **LC-Akte aus Avis anlegen** erzeugt die Akte aus allen Feldern des Akkreditivtextes (wie ein MT700-Import, inkl. Dokumentenanforderungen, spätester
   Versand, Warenbeschreibung) und legt den Avis in der Akte als Dokument „Avisierungsschreiben" ab. Beides geschieht in einer Transaktion.

Die Funktion steht Bank- und Corporate-Mandanten zur Verfügung (Recht „LC bearbeiten" und „Dokumente hochladen"). Erkannte Angaben sind Vorschläge; bitte mit
dem Original vergleichen. Enthält ein Avis den Akkreditivtext nicht in dieser Form, meldet die Vorschau das, und die Akte wird manuell angelegt.

## Avis ohne Akkreditivtext (nur Anschreiben)
Steht im Avis nur das Anschreiben („Den Inhalt entnehmen Sie bitte der beigefügten Kopie"), zeigt „Als Avis einlesen" die erkannten Angaben des Anschreibens (Akkreditivnummer, Betrag, Währung, Gültig bis und Ort, eröffnende Bank, Auftraggeber, Begünstigter aus der Anschrift) als änderbares Formular und legt daraus die LC-Akte an („LC-Akte aus Anschreiben anlegen"); das Anschreiben wird als Avisierungsschreiben in der Akte abgelegt. Geforderte Dokumente fehlen dann und sind später nachzutragen (oder die Kopie des Akkreditivtextes einzuspielen).

## SWIFT-Nachrichten im Posteingang (Stand 15.10.2026)
Erkennt der Posteingang eine Datei als SWIFT-Nachricht (MT700), heißt der Knopf **„Als SWIFT-Nachricht importieren …"** (auch bei TXT/FIN-Dateien). Er nutzt denselben Import wie der SWIFT-Dialog, einschließlich der Bank-Druckansicht „27: SEQUENCE OF TOTAL" mit Wert darunter. Eine MT700 legt die Akte an und die Datei als „SWIFT MT700" ab (beim Avis mit Anschreiben weiterhin als Avisierungsschreiben). Eine **MT707** wird als offene Änderung der vorhandenen Akte übernommen (dort annehmen oder ablehnen); die Nachricht liegt als Dokument in der Akte. Ein Anschreiben mit Grußformel vor den Feldern schneidet den Akkreditivtext nicht mehr ab.

## Akkreditivtext mit Feldnamen, aber ohne SWIFT-Feldnummern (Stand 19.10.2026)
Ausdrucke aus Bankportalen und Handelsfinanzsystemen tragen oft nur die Feldnamen, auf Englisch oder Deutsch, zum Beispiel „Documentary Credit Number: LC-1", „Akkreditivnummer   LC-1" oder „Betrag: EUR 125.000,00". Auch diese Texte werden gelesen (`LabelledCreditNormalizer`), wenn der Text mit Feldnummern nichts ergibt:

- Der Name steht am Zeilenanfang, mit Doppelpunkt oder zwei Leerzeichen dahinter, oder allein in der Zeile; der Wert steht dahinter oder in den folgenden Zeilen.
- Erkannt werden Akkreditivnummer, Ausstellungs- und Verfallsdatum mit Ort, Betrag mit Währung, Auftraggeber, Begünstigter, eröffnende und avisierende Bank, Teilverladungen, Umladung, Verlade- und Entladehafen, spätester Verladetag, Warenbeschreibung, geforderte Dokumente, zusätzliche Bedingungen, Vorlagefrist, Bestätigung, Art des Akkreditivs und Regelwerk.
- Daten (`05.10.2026`, `5. Oktober 2026`, `October 5, 2026`, `2026-10-05`, `05/10/26`, Schrägstrich-Daten tagesvoran) und Beträge (`EUR 125.000,00`, `125,000.50 USD`, `CHF 1'250'000`) werden in die MT700-Form umgewandelt.
- Sicherungen: Der Text zählt nur als Akkreditiv, wenn Akkreditivnummer und Betrag und mindestens zwei weitere Felder gefunden werden (ein Anschreiben mit „Referenz" und „Betrag" wird nicht fälschlich als Akkreditiv gelesen). Kommt ein Name ein zweites Mal vor, gilt die erste Lesung. Seitenköpfe und -fußer („Seite 2 von 3", „Page 2 of 3") werden auch mitten in einem Feld entfernt. Steht die Anlage hinter einem Anschreiben mit Grußformel, wird auch der Text zwischen den Grußformeln gelesen und die Lesung mit den meisten Feldern gewählt, damit die Anlage nicht abgeschnitten wird.
- Offen: Schreiben und Anlage als zwei getrennte Dateien werden noch jeweils für sich eingelesen.

Synthetische Beispiele zum Testen: `src/test/resources/avis/` (alle Firmen und Zahlen erfunden).
