# Prüfmodus, Aktenliste und Prüfbericht

Für eine geführte Prüfung Schritt für Schritt gibt es den [Prüfungsassistenten](pruefassistent.md); er ruft diesen Prüfmodus beim Schritt „Dokumente" auf.

## Dokumentweise Prüfung (Prüfmodus)
Aus der Akte („Dokumentweise prüfen") oder direkt aus der Aktenliste (Knopf **Prüfen**) öffnet sich ein Dialog: links die Befunde des aktuellen Dokuments, rechts das PDF.

* **Fortschritt** über die ganze Akte („14 / 41 erledigt") und eine **Dokumentleiste** mit allen Dokumenten: Zahl der offenen Punkte, „!" bei blockierenden, „✓" wenn erledigt; ein Klick springt zum Dokument.
* **Stufen:** *Blockierend* (Betrag, Ablauf-/Vorlagefrist, Verladedatum, fehlendes Dokument, Unterschrift, Sanktionen, Embargo, Exportkontrolle) steht oben, dann *Prüfen* (übrige Abweichungen); *Hinweise* sowie *Erledigt & in Ordnung* sind zugeklappt.
* **Tastenkürzel:** J = als erfüllt bestätigen, N = Abweichung bestätigen, ↑/↓ = Befund, ←/→ = Dokument.
* Nach dem letzten offenen Punkt eines Dokuments geht es automatisch zum nächsten.
* **Gleicher Befund in mehreren Dokumenten:** Die Karte zeigt „Gleicher Befund auch in: …" und „Für alle N … bestätigen". Eine Begründung gilt für alle; eine markierte Fundstelle nur für das aktuelle Dokument. Befunde zum Dokumentensatz werden nicht gebündelt.
* **Fundstelle markieren** statt Kommentar: Rechteck im PDF; ohne Markierung ist bei „erfüllt" eine Begründung Pflicht.

## Fundstelle, Haken und fehlende Angaben (Stand 17.10.2026)
* **Gelbe Zeile im PDF:** Beim Öffnen eines Befunds springt das PDF auf die Seite und markiert die Zeile, auf die sich der Befund bezieht (höchstens 5 Zeilen, bei Unsicherheit keine). Gefunden wird wörtlich zitierter Text, das Dokumentdatum, die erkannte Referenz und bei Regel-Befunden der **geprüfte Wert** in üblichen Schreibweisen (Betrag 125,000.00 / 125.000,00, Datumsformate wie „20 DEC 2026", Texte ohne Beachtung der Groß-/Kleinschreibung; kleine Zahlen wie die Menge „2" und Ja/Nein-Werte nie). Bei Text-PDFs kommt die Zeile aus dem PDF-Text, bei Scans aus den OCR-Wörtern (Seiten mit Drehkorrektur ausgenommen). Gelb ist nur eine Hilfe fürs Auge, keine Entscheidung.
* **Roter Haken:** Zu jedem entschiedenen Befund steht am Dokument ein kleiner roter Haken (als erfüllt bestätigt) bzw. ein rotes Kreuz (Abweichung bestätigt), rechts neben der Zeile des Befunds, sonst an der selbst markierten Fundstelle. Der Hinweis (Tooltip) nennt Entscheidung und Prüfer. Befunde ohne auffindbare Stelle (z. B. fehlendes Dokument) bekommen keinen Haken. Nur auf der angezeigten Seite; nach jeder Entscheidung aktualisiert.
* **Fehlende LC-Angaben:** Regeln, die nur an einer fehlenden LC-Angabe scheitern („Regel nicht prüfbar … LC_… = nicht erfasst"), stehen nicht bei jedem Dokument, sondern **einmal** im gelben Kasten über der Dokumentleiste („Vorlagedatum · 6 Regeln, 5 Dokumente"). Das **Vorlagedatum** wird dort direkt gespeichert (Vorschlag: heute, kein Datum in der Zukunft; `PUT /api/lcs/{id}/rule-facts/presentation-date`, Recht „LC bearbeiten", Audit wie bei den LC-Prüfdaten, andere Prüfdaten bleiben unverändert); andere Angaben öffnen die LC-Prüfdaten. Danach werden die Befunde neu berechnet. Im Bearbeiten-Dialog der Akte zeigt der Status „Dokumente eingereicht" das Feld **Vorlagedatum** (heute oder das gespeicherte Datum).
* **Fortschritt und Abschluss:** Oben steht „14 / 21 Befunde erledigt · 3 / 6 Dokumente fertig". Sind alle Befunde entschieden und keine LC-Angaben offen, erscheint der **Abschlusskasten**: Ergebnis (N erfüllt, M bestätigte Unstimmigkeiten), bei Unstimmigkeiten „Unstimmigkeitsmeldung erstellen", **„Prüfung abschließen"** (schreibt einen Vermerk mit dem Ergebnis in die Akte; Recht „LC bearbeiten", Audit) und „Prüfakte exportieren" (Recht Audit). Der Status der Akte ändert sich dadurch nicht; eine echte Freigabe gibt es dafür nicht.

## Erklärung der Abweichung

Bei verletzten Datums- und Zahlenvergleichen steht unter dem Befund eine „Rechnung“ in Worten, zum Beispiel: *Versanddatum 18.09.2026, spätestens zulässiges Versanddatum 15.09.2026: 3 Tage nach der zulässigen Grenze.* Beim Rechnungsbetrag zeigt sie LC-Betrag, Toleranz, Höchstbetrag und den Überschuss. Nur bei lesbaren Werten und einfachen Vergleichen (gleich, höchstens, mindestens); sonst bleibt der Befund wie bisher. Die Erklärung ändert weder Schweregrad noch Prüffingerabdruck.

## Zusatzbedingungen (:47A:)

Die Zusatzbedingungen werden in ihre nummerierten Positionen zerlegt. Zwei häufige Positionen prüft die App je Dokument selbst:

- „Alle Dokumente müssen die Akkreditivnummer zeigen“: Die LC-Referenz (:20:) muss im Dokumenttext vorkommen (Groß-/Kleinschreibung, Leerzeichen und Trennzeichen werden ignoriert). Fehlt sie, ist das eine Abweichung.
- „Alle Dokumente müssen in englischer Sprache ausgestellt sein“: Eine einfache Spracherkennung über häufige Funktionswörter. Eindeutig andere Sprache ist eine Abweichung, unklare oder zu kurze Texte sind ein Hinweis zum Prüfen.

Alle anderen Positionen bleiben beim Sammelhinweis „Zusatzbedingungen müssen fachlich … geprüft werden“; sind alle Positionen automatisch prüfbar, entfällt der Sammelhinweis. Nicht lesbare Dokumente werden als „nicht prüfbar“ gemeldet.

## Prüfvermerk mit Vorlagen
Begründungen werden in einem eigenen Dialog erfasst (auch bei Sammelbestätigungen). **Passend zum Befund** (Partei/Adresse, Ware, Betrag/Menge, Datum, Verladung, Versicherung, Original/Unterschrift) werden Bausteine mit Regelgrundlage vorgeschlagen, z. B. „Adresse abweichend, gleiches Land – zulässig (UCP 600 Art. 14(j))"; die Artikelangaben sind nicht fachlich geprüft und vor der Verwendung zu kontrollieren. Reihenfolge: eigene Vorlagen, passende Bausteine, allgemeine. Die Tasten **1–9** wählen einen Baustein (solange das Feld leer ist), **Enter** speichert, Umschalt+Enter macht eine neue Zeile. Häufige Texte stehen als Vorlagen zum Anklicken bereit („Original geprüft, Angabe im Dokument vorhanden." …); mit **„Als Vorlage merken"** kommen eigene dazu (pro Browser, höchstens 12).

## Aktenliste und Cockpit
* Jede Akte zeigt **„X offen · Y blockierend"** (Server: `GET /api/lcs/dossier-status`, Felder `open`, `blocking`, `sanctions`) und den Knopf **Prüfen**.
* Cockpit „Heute zu tun": **Frist knapp, blockiert** (offene Akten mit blockierenden Befunden und Ablauf- oder Verladefrist in höchstens 5 Tagen) und **Sanktionstreffer offen**. Bei genau einer Akte öffnet die Kachel direkt den Prüfmodus.

## Prüfbericht (PDF)
Bei Entscheidungen mit markierter Fundstelle steht „Marked evidence: <Dokument>, page N" und darunter ein Bildausschnitt der markierten Stelle; ohne Markierung „not marked".

Siehe auch [Sanktionsprüfung](sanktionspruefung.md) (Vier-Augen-Freigabe von Sanktionstreffern) und [HS-Code-Erkennung](hs-codes.md).

## Unstimmigkeitsmeldung (Entwurf)
Im Prüfmodus erzeugt **„Unstimmigkeitsmeldung (Entwurf)“** aus allen *bestätigten* Abweichungen der Akte zwei Entwürfe: eine **SWIFT MT734** (Advice of Refusal: :20: eigene Referenz, :21: Referenz der Gegenbank oder NONREF, :32A: Datum/Währung/Betrag, :77J: die Unstimmigkeiten nummeriert, im SWIFT-Zeichensatz, Zeilen höchstens 65 Zeichen, höchstens 70 Zeilen, :77B: Dokumente zur Verfügung) und ein **Schreiben an die Bank** (mit Prüfvermerken). Beide sind im Dialog änderbar und kopierbar; versendet wird nichts. Betrag und :21: bitte ergänzen bzw. prüfen.
