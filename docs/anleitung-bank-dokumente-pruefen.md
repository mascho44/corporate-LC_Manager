# Anleitung: Bank, Dokumente prüfen

Für Mitarbeitende einer Bank, die unter einem Akkreditiv eingereichte Dokumente prüfen. Den allgemeinen Teil (Anmeldung, Oberfläche, Akte) finden Sie in der [Anleitung: Allgemeiner Teil](anleitung-allgemein.md).

Benötigte Rechte: „LC ansehen“, „Dokumente prüfen“, für den Abschlussvermerk „LC bearbeiten“, für Sanktionstreffer „Sanktionstreffer freigeben“, für den Export der Prüfakte „Audit ansehen“. Der Mandant muss das **Bank-Profil** haben.

**Wichtig:** Die Anwendung unterstützt Ihre Prüfung. Sie ersetzt nicht die fachliche Entscheidung nach ISBP und UCP 600. Ein grünes Ergebnis bedeutet nur, dass die eingebauten Regeln nichts gefunden haben. Es ist keine Freigabe und prüft weder die Echtheit noch physische Originale.

**Der Ablauf in Kürze**

1. Akte und Dokumente bereitstellen
2. Prüfungsassistent starten
3. Befunde im Prüfmodus entscheiden
4. Unstimmigkeiten melden
5. Prüfung abschließen und dokumentieren

## 1. Akte und Dokumente bereitstellen

1. **Akkreditiv:** Die Akte kommt aus der MT700 (und ggf. Änderungen MT707). Prüfen Sie, dass alle Änderungen **angenommen oder abgelehnt** sind. Geprüft wird gegen die **geltende Fassung**; offene Änderungen erzeugen einen Hinweis.
2. **Eingereichte Dokumente:** Laden Sie sie in der Akte hoch oder ordnen Sie sie im **Dokumentenposteingang** zu (Zuordnungsvorschlag bestätigen). Mehrseitige Sammeldateien lassen sich aufteilen.
3. **Original/Kopie** und **Dokumenttyp** richtig setzen. Die Anzahl der Originale und Kopien wird anhand dieser Angaben mit der Forderung verglichen.
4. **Vorlagedatum** erfassen (Datum der Einreichung). Ohne dieses Datum bleiben Regeln „nicht prüfbar“.
5. Scans und Fotos lesen Sie per OCR ein. Prüfen Sie bei schlechter Qualität den erkannten Text gegen das PDF.

In der **Aktenliste** zeigt jede Akte „X offen · Y blockierend“. Der Knopf **Prüfen** öffnet den Prüfmodus, **Assistent** den Prüfungsassistenten. Im **Cockpit** (Heute zu tun) sehen Sie knappe Fristen und offene Sanktionstreffer.

## 2. Prüfungsassistent: sieben Schritte

Der **Prüfungsassistent** führt durch die Prüfung in fester Reihenfolge. Die Schritte:

| Schritt | Was Sie tun |
|---|---|
| 1 Akte und Bedingungen | Parteien, Betrag, Ablauf und Ort, spätester Versand, geforderte Dokumente prüfen; bei Fehlern „Akte bearbeiten“ |
| 2 Vollständigkeit | Vorliegende Dokumente mit Original/Copy; Befunde zu fehlenden Dokumenten, Zahl der Originale und Kopien, Full Set |
| 3 Fehlende Angaben | LC-Angaben ergänzen, ohne die Regeln „nicht prüfbar“ bleiben (zum Beispiel Vorlagedatum) |
| 4 Compliance | Sanktionen, Embargo, Exportkontrolle, HS-Code |
| 5 Dokumente | Übersicht je Dokument, Sprung in den dokumentweisen Prüfmodus |
| 6 Querprüfungen und Fristen | Konsistenz über Dokumente (Betrag, Menge, Daten, Zusatzbedingungen); Ablauf, Versand, Vorlagefrist |
| 7 Abschluss | Ergebnis, übersprungene Schritte, Unstimmigkeitsmeldung, Abschlussvermerk, Export |

So bedienen Sie ihn:

- Links die Schrittleiste (✓ erledigt, ↷ übersprungen, rote Zahl = offene Befunde), oben der Fortschritt.
- **Schritt erledigt ✓** geht nur, wenn im Schritt nichts mehr offen ist. Sonst **Überspringen …** mit **Pflichtbegründung**. Der Grund steht im Assistenten, im Abschluss und im Audit. **Wieder öffnen** nimmt einen Eintrag zurück.
- **Zurück/Weiter** wechseln nur die Ansicht.
- Der Fortschritt wird **je Akte** gespeichert. Eine Kollegin kann an derselben Stelle weitermachen.
- Ein erledigter Schritt bleibt erledigt, auch wenn später neue Befunde entstehen (zum Beispiel nach einem weiteren Upload). Der Schrittzähler zeigt sie dann als offene Zahl.

### Schritt 3: Fehlende Angaben

Der Schritt listet LC-Angaben, ohne die Regeln nicht prüfbar sind. Die App liest, soweit möglich, Werte aus dem Akkreditiv (zum Beispiel Zahl der Originale und Unterschrift aus Feld 46A) und schlägt sie zur Übernahme vor. Verlangt das Akkreditiv eine Angabe nicht, markieren Sie sie als **„Im Akkreditiv nicht verlangt“**; betroffene Regeln gelten dann als nicht anwendbar. Die Markierung wird im Audit vermerkt und lässt sich aufheben.

### Schritt 4: Compliance

- **Sanktionen:** Alle Parteien (Auftraggeber, Begünstigter, eröffnende, avisierende, bestätigende Bank …) werden gegen EU-, UN- und OFAC-Listen geprüft; dazu kommen Schiffe (IMO-Nummer, Name) sowie Empfänger und Notify Party in den Dokumenten. Treffer ab 0,92 sind **Abweichungen**, mögliche Treffer ab 0,80 **Hinweise**, beide **blockierend**.
- **Embargoländer** und **Exportkontrolle**/**HS-Codes** erscheinen ebenfalls blockierend.
- Einen Treffer, der keiner ist, markieren Sie als **Falsch-positiv**. Er braucht eine **zweite Freigabe** durch eine Person mit dem Recht „Sanktionstreffer freigeben“ (Vier-Augen-Prinzip). Die Sanktionsprüfung ersetzt kein zertifiziertes Screening-System ([Sanktionsprüfung](sanktionspruefung.md)).

## 3. Befunde im Prüfmodus entscheiden

Aus der Akte („Dokumentweise prüfen“), dem Assistenten (Schritt 5) oder der Aktenliste (**Prüfen**) öffnet der Prüfmodus: links die Befunde des aktuellen Dokuments, rechts das PDF.

**Aufbau**

- Oben der Fortschritt („14 / 41 erledigt“), darunter die **Dokumentleiste**: Zahl offener Punkte, „!“ bei blockierenden, „✓“ wenn erledigt. Ein Klick springt zum Dokument.
- **Stufen:** *Blockierend* (Betrag, Ablauf- oder Vorlagefrist, Verladedatum, fehlendes Dokument, Unterschrift, Sanktionen, Embargo, Exportkontrolle) steht oben, dann *Prüfen* (übrige Abweichungen). *Hinweise* und *Erledigt & in Ordnung* sind zugeklappt.
- Jeder Befund nennt die **Regel**, die **LC-Bedingung**, die **Fundstelle** und, bei Datum und Zahlen, die **Rechnung in Worten**.

**Fundstelle im PDF**

- Beim Öffnen eines Befunds springt das PDF zur Seite und markiert die betreffende **Zeile gelb** (höchstens fünf Zeilen, bei Unsicherheit keine). Gelb ist nur eine Orientierungshilfe, keine Entscheidung.
- Eine **eigene Fundstelle** markieren Sie als Rechteck im PDF. Ohne Markierung ist bei „erfüllt“ eine Begründung Pflicht.
- Entschiedene Befunde zeigen am Dokument einen kleinen **roten Haken** (erfüllt) oder ein **rotes Kreuz** (Abweichung); der Tooltip nennt Entscheidung und Prüfer.

**Entscheiden**

- **J** = als erfüllt bestätigen, **N** = Abweichung bestätigen, **↑/↓** = Befund wechseln, **←/→** = Dokument wechseln.
- Begründungen erfassen Sie in einem Dialog. Passende **Bausteine** mit Regelgrundlage (zum Beispiel „Adresse abweichend, gleiches Land – zulässig (UCP 600 Art. 14(j))“) werden vorgeschlagen; die Ziffern 1–9 wählen einen Baustein, Enter speichert. **Prüfen Sie die Artikelangaben der Bausteine selbst**, sie sind nicht fachlich verbürgt. Eigene Vorlagen merken Sie sich mit „Als Vorlage merken“.
- Nach dem letzten offenen Punkt eines Dokuments geht es automatisch zum nächsten.
- **Gleicher Befund in mehreren Dokumenten:** „Für alle N … bestätigen“ gilt für alle mit einer gemeinsamen Begründung. Eine markierte Fundstelle gilt nur für das aktuelle Dokument.
- **Sammelbestätigung** in der Befundliste: Hat eine Regel mindestens zwei offene **Hinweise**, bestätigen Sie alle als erfüllt, mit Pflichtbegründung. **Abweichungen** sind nie betroffen.

**Typische Prüfungen der Software**

- Geforderte Dokumente vorhanden, Zahl der Originale und Kopien (Full Set)
- Betrag innerhalb Akkreditivbetrag plus Toleranz, Währung
- Daten gegen spätesten Versand, Vorlagefrist und Ablauf
- Referenz, Parteien und Waren in den Dokumenten
- Zusatzbedingungen (:47A:): Akkreditivnummer auf allen Dokumenten, englische Sprache; alle anderen Positionen bleiben **manuelle Prüfung**
- Unbekannte oder nicht lesbare Anforderungen werden als **nicht prüfbar** gemeldet. Sie entscheiden diese immer selbst.

Wichtig: Die Software findet Hinweise, **Sie** entscheiden. Prüfen Sie Unterschriften, Stempel, Zahl der physischen Originale und Übereinstimmung mit der Ware anhand der Papierunterlagen.

## 4. Unstimmigkeiten melden

Im Prüfmodus oder in Schritt 7 des Assistenten erzeugt **„Unstimmigkeitsmeldung (Entwurf)“** aus allen **bestätigten Abweichungen** zwei Entwürfe:

- eine **SWIFT MT734** (Advice of Refusal) mit eigener Referenz, Referenz der Gegenbank oder NONREF, Betrag, den nummerierten Unstimmigkeiten (Zeilen höchstens 65 Zeichen, höchstens 70 Zeilen) und dem Hinweis, dass die Dokumente zur Verfügung stehen
- ein **Schreiben an die Bank** mit den Prüfvermerken

Beide Entwürfe sind änderbar und kopierbar. **Versendet wird nichts.** Ergänzen Sie Betrag und die Referenz in :21: und senden Sie die Nachricht über Ihr SWIFT-System. Beachten Sie die Prüffristen nach UCP 600.

## 5. Prüfung abschließen und dokumentieren

1. Sind alle Befunde entschieden und keine LC-Angaben offen, erscheint der **Abschlusskasten**: Ergebnis (N erfüllt, M bestätigte Unstimmigkeiten).
2. **„Prüfung abschließen“** schreibt einen **Vermerk mit dem Ergebnis** in die Akte (Recht „LC bearbeiten“, im Audit protokolliert) und setzt die Akte auf den Status **„Prüfung abgeschlossen“**. Eine Freigabe oder Zahlung löst das nicht aus.
3. **Betragsgrenze:** Bis zu einer vom Administrator festgelegten Grenze (Einstellungen, „Prüfabschluss“) schließt eine Person allein ab. Darüber merkt Ihr Klick den Abschluss nur vor („Abschluss vorgemerkt von …“); eine **andere Person** zeichnet ihn mit **„Abschluss mitzeichnen“**. Je nach Stufe sind bis zu fünf verschiedene Personen nötig. Wer begonnen hat, kann den offenen Abschluss zurückziehen.
4. **„Prüfakte exportieren“** (Recht Audit) erstellt den Prüfbericht als PDF. Bei Entscheidungen mit markierter Fundstelle enthält er den Bildausschnitt der Stelle.
5. Alle Entscheidungen, übersprungenen Schritte, Begründungen, Abschlüsse und Freigaben stehen im **Audit-Protokoll**.

## Nach einer Änderung

Ändert sich die Akte (zum Beispiel durch eine angenommene Änderung) oder werden Dokumente ersetzt, werden Prüfentscheidungen der Akte zurückgesetzt bzw. Befunde neu berechnet. Prüfen Sie dann den Assistenten erneut; neue Befunde erscheinen als offene Zahl an den Schritten.

## Checkliste für die Prüfung

- [ ] Alle Änderungen entschieden, geltende LC-Fassung klar
- [ ] Vollständigkeit: alle verlangten Dokumente, Originale und Kopien stimmen
- [ ] Vorlagedatum erfasst, Fristen (Versand, Vorlage, Ablauf) gewahrt
- [ ] Sanktions-, Embargo- und Exportkontrolltreffer entschieden, Falsch-positive zweitfreigegeben
- [ ] Jedes Dokument gegen seine Anforderung geprüft, Fundstellen nachvollziehbar
- [ ] Querprüfung: Betrag, Menge, Ware, Parteien und Daten über alle Dokumente konsistent
- [ ] Zusatzbedingungen, die die Software nicht prüft, manuell erledigt
- [ ] Unstimmigkeitsmeldung erzeugt und über das SWIFT-System versendet (falls nötig)
- [ ] Prüfung abgeschlossen (bei hohen Beträgen mitgezeichnet), Prüfbericht exportiert

## Häufige Fragen

| Frage | Antwort |
|---|---|
| Viele Regeln „nicht prüfbar“ | In Schritt 3 die fehlende LC-Angabe ergänzen (häufig das Vorlagedatum) oder als „nicht verlangt“ markieren |
| „Schritt erledigt“ lässt sich nicht klicken | Im Schritt sind noch Befunde offen; entscheiden oder mit Begründung überspringen |
| Dokument wurde falsch erkannt | Dokumenttyp, Datum, Original/Kopie im Dokument korrigieren |
| Der Haken im PDF fehlt | Für den Befund wurde keine Stelle gefunden (zum Beispiel fehlendes Dokument); eigene Fundstelle markieren |
| Eine Entscheidung ist plötzlich wieder offen | Der Befund hat sich geändert (neue Regelversion oder geänderte Akte); neu bestätigen |
| Kann ich damit freigeben oder zahlen? | Nein. Der Abschluss setzt nur den Status „Prüfung abgeschlossen“; Freigabe und Zahlung laufen außerhalb |
| „Abschluss mitzeichnen“ fehlt | Sie haben selbst begonnen oder schon gezeichnet; eine andere Person mit dem Recht „LC bearbeiten“ muss mitzeichnen |
