# Prüfmodus, Aktenliste und Prüfbericht

## Dokumentweise Prüfung (Prüfmodus)
Aus der Akte („Dokumentweise prüfen") oder direkt aus der Aktenliste (Knopf **Prüfen**) öffnet sich ein Dialog: links die Befunde des aktuellen Dokuments, rechts das PDF.

* **Fortschritt** über die ganze Akte („14 / 41 erledigt") und eine **Dokumentleiste** mit allen Dokumenten: Zahl der offenen Punkte, „!" bei blockierenden, „✓" wenn erledigt; ein Klick springt zum Dokument.
* **Stufen:** *Blockierend* (Betrag, Ablauf-/Vorlagefrist, Verladedatum, fehlendes Dokument, Unterschrift, Sanktionen, Embargo, Exportkontrolle) steht oben, dann *Prüfen* (übrige Abweichungen); *Hinweise* sowie *Erledigt & in Ordnung* sind zugeklappt.
* **Tastenkürzel:** J = als erfüllt bestätigen, N = Abweichung bestätigen, ↑/↓ = Befund, ←/→ = Dokument.
* Nach dem letzten offenen Punkt eines Dokuments geht es automatisch zum nächsten.
* **Gleicher Befund in mehreren Dokumenten:** Die Karte zeigt „Gleicher Befund auch in: …" und „Für alle N … bestätigen". Eine Begründung gilt für alle; eine markierte Fundstelle nur für das aktuelle Dokument. Befunde zum Dokumentensatz werden nicht gebündelt.
* **Fundstelle markieren** statt Kommentar: Rechteck im PDF; ohne Markierung ist bei „erfüllt" eine Begründung Pflicht.

## Prüfvermerk mit Vorlagen
Begründungen werden in einem eigenen Dialog erfasst (auch bei Sammelbestätigungen). Häufige Texte stehen als Vorlagen zum Anklicken bereit („Original geprüft, Angabe im Dokument vorhanden." …); mit **„Als Vorlage merken"** kommen eigene dazu (pro Browser, höchstens 12).

## Aktenliste und Cockpit
* Jede Akte zeigt **„X offen · Y blockierend"** (Server: `GET /api/lcs/dossier-status`, Felder `open`, `blocking`, `sanctions`) und den Knopf **Prüfen**.
* Cockpit „Heute zu tun": **Frist knapp, blockiert** (offene Akten mit blockierenden Befunden und Ablauf- oder Verladefrist in höchstens 5 Tagen) und **Sanktionstreffer offen**. Bei genau einer Akte öffnet die Kachel direkt den Prüfmodus.

## Prüfbericht (PDF)
Bei Entscheidungen mit markierter Fundstelle steht „Marked evidence: <Dokument>, page N" und darunter ein Bildausschnitt der markierten Stelle; ohne Markierung „not marked".

Siehe auch [Sanktionsprüfung](sanktionspruefung.md) (Vier-Augen-Freigabe von Sanktionstreffern) und [HS-Code-Erkennung](hs-codes.md).

## Unstimmigkeitsmeldung (Entwurf)
Im Prüfmodus erzeugt **„Unstimmigkeitsmeldung (Entwurf)“** aus allen *bestätigten* Abweichungen der Akte zwei Entwürfe: eine **SWIFT MT734** (Advice of Refusal: :20: eigene Referenz, :21: Referenz der Gegenbank oder NONREF, :32A: Datum/Währung/Betrag, :77J: die Unstimmigkeiten nummeriert, im SWIFT-Zeichensatz, Zeilen höchstens 65 Zeichen, höchstens 70 Zeilen, :77B: Dokumente zur Verfügung) und ein **Schreiben an die Bank** (mit Prüfvermerken). Beide sind im Dialog änderbar und kopierbar; versendet wird nichts. Betrag und :21: bitte ergänzen bzw. prüfen.
