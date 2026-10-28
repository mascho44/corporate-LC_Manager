# Anleitung: Corporate, Dokumente erstellen und einreichen

Für Mitarbeitende eines Unternehmens (Exporteur, Begünstigter eines Akkreditivs), die die Dokumente für eine Lieferung erstellen, vorab prüfen und bei der Bank einreichen. Den allgemeinen Teil (Anmeldung, Oberfläche, Akte) finden Sie in der [Anleitung: Allgemeiner Teil](anleitung-allgemein.md).

Benötigte Rechte: „LC ansehen/bearbeiten“, „Dokumente hochladen“, „Dokumente erstellen“. Fehlt Ihnen etwas, fragen Sie den Mandantenadministrator.

**Der Ablauf in Kürze**

1. Akte öffnen und Rolle prüfen
2. Beschaffungsplan ansehen
3. Dokumente erzeugen
4. Dokumente von Dritten hinzufügen
5. Vorprüfung und Einreichungs-Check
6. Einreichungspaket erstellen und bei der Bank einreichen
7. Fristen im Blick behalten

## 1. Akte öffnen und Rolle prüfen

1. Öffnen Sie im Menü **LC-Akten** die Akte Ihres Akkreditivs. Ist sie noch nicht vorhanden, importieren Sie die MT700 über **SWIFT importieren** oder lesen Sie das Avisierungsschreiben der Bank ein (siehe Allgemeiner Teil, Abschnitt 5).
2. Prüfen Sie oben in der Akte **„Unsere Rolle in dieser Akte“**: **Begünstigter (Exporteur)** oder **Antragsteller (Importeur)**. Beschaffungsplan, Kalendertermine, Erinnerungen und der Einreichungs-Check gelten nur bei der Rolle **Begünstigter**. Standard ist Begünstigter.
3. Lesen Sie die Bedingungen durch: Betrag und Toleranz (:39A:), spätester Versand (:44C:), Ablauf, Zusatzbedingungen (:47A:), geforderte Dokumente (:46A:). Prüfen Sie auch, ob eine Änderung (MT707) offen ist und noch angenommen werden muss.

## 2. Beschaffungsplan ansehen

Oben in der Akte zeigt der **Beschaffungsplan**, welche Dokumente das Akkreditiv verlangt, was schon vorhanden ist und bis wann was fällig ist.

- Das wichtigste Datum ist der **späteste Versandtermin**. Das Transportdokument (B/L, Luftfrachtbrief, CMR usw.) ist an diesem Tag fällig.
- Alle anderen Dokumente werden in **Arbeitstagen rückwärts** gerechnet (Richtwerte: Rechnung 2, Packliste 3, Versicherung 3, Ursprungszeugnis 7, Inspektions-/Qualitätszertifikat 10, übrige 5 Tage). Feiertage sind nicht berücksichtigt.
- Ampel je offenem Dokument: **überfällig**, **kritisch** (höchstens 3 Tage), **bald fällig** (höchstens 10 Tage), **im Plan**.
- Mit „LC bearbeiten“ setzen Sie je fehlendem Dokument den Stand **Nicht begonnen / In Arbeit / Beantragt**, mit Notiz. Das ändert die Ampel nicht, zeigt aber den Kollegen, wer woran arbeitet. „Vorhanden“ gilt erst, wenn ein Dokument dieses Typs hochgeladen wurde.
- Administratoren können die Vorlaufzeiten über **Vorlaufzeiten** anpassen.

## 3. Dokumente erzeugen

Voraussetzung: Die **Firmenstammdaten** (Name, Anschrift, Bank, Logo) sind gepflegt; sie bilden Briefkopf und Fußzeile aller Dokumente.

1. Klicken Sie in der Akte auf **Dokumentensatz erzeugen**.
2. Der Dialog macht einen **Vorschlag** aus dem Akkreditiv: die verlangten Dokumente, eine Rechnungsnummer (`INV-<Jahr>-<laufende Nummer>`), das heutige Datum und eine Position aus der Warenbeschreibung. Zu jedem Dokument steht, ob es verlangt ist, wie viele Originale und ob eine Unterschrift gefordert sind.
3. Prüfen und ergänzen Sie:
   - Rechnungsnummer und Datum
   - **Positionen:** Beschreibung (so, wie das Akkreditiv sie nennt), Menge, Einheit, Einzelpreis, Betrag, Packstücke, Netto-/Bruttogewicht
   - Hinweise und Markierungen
   Ein leerer Betrag ergibt sich aus Menge mal Einzelpreis.
4. Beachten Sie die Anzeige unter den Positionen: Sie warnt sofort, wenn die **Summe über dem zulässigen Höchstbetrag** (Akkreditivbetrag plus Toleranz) liegt. Korrigieren Sie das vor dem Erzeugen.
5. Wählen Sie das **Format**: **Word** (bearbeitbar, nutzt Ihre Firmenvorlage) oder **PDF** (fertig zum Versand, Standardlayout).
6. Klicken Sie auf **Erzeugen**. Je Dokument entsteht eine Datei in der Akte, die Nummern leiten sich von der Rechnungsnummer ab (`INV-2026-0815` wird zu `PL-2026-0815`, `COO-…`).
7. Lesen Sie das Ergebnis: **Hinweise** (Summe zu hoch, Datum nach Verfall, fehlende Packstücke) und die **Befunde der automatischen Prüfung** an den neuen Dokumenten. Hinweise zu Originalen, Unterschrift und Aussteller bleiben bewusst stehen: Sie sind manuell zu klären.

**Tipps**

- Rechnungsbetrag, Menge, Warenbeschreibung, Parteien und Daten müssen in **allen** Dokumenten gleich sein und den Wortlaut des Akkreditivs treffen.
- Verlangt das Akkreditiv bestimmte Angaben auf allen Dokumenten (zum Beispiel die Akkreditivnummer oder die englische Sprache), prüft die Anwendung das ebenfalls.
- Eigene Word-Vorlagen laden Administratoren unter „Dokumentvorlagen“ hoch ([Word-Vorlagen](word-vorlagen.md)).

## 4. Dokumente von Dritten hinzufügen

Frachtbriefe, Versicherungen, Zeugnisse und Ähnliches stellen Spediteur, Versicherer oder Behörden aus.

- **Hochladen:** in der Akte „Dokument hochladen“, oder über den **Dokumentenposteingang** (mit Zuordnungsvorschlag, den Sie bestätigen). Spediteure können über einen **Erfassungs-Link** selbst Dokumente einreichen ([Erfassungszugang](erfassungszugang.md)).
- Kennzeichnen Sie **Original oder Kopie** und das richtige **Dokumentdatum**. Die Prüfung gleicht beides mit den Forderungen ab.
- **Entwurf vorab prüfen:** Bevor Sie einen Entwurf (zum Beispiel ein Konnossement) freigeben, nutzen Sie **„Entwurf prüfen“**. So finden Sie Abweichungen, solange sie sich noch ändern lassen.

## 5. Vorprüfung und Einreichungs-Check

Über der Prüfung zeigt die Karte **Einreichungsbereit** oder **Noch nicht einreichungsbereit**. Sie zählt offene Abweichungen, auch fehlende Dokumente. Hinweise werden erwähnt, blockieren aber nicht.

1. Öffnen Sie über **Alle Befunde anzeigen** die Liste. Ab sechs Befunden helfen Filter nach Art, Dokument und Regel.
2. Zu jedem Befund sehen Sie die **LC-Bedingung**, den **Beleg im Dokument** und bei Datum und Zahlen eine **Rechnung in Worten** („Versanddatum 18.09.2026, spätestens zulässig 15.09.2026: 3 Tage zu spät“).
3. Beheben Sie Abweichungen **an der Quelle**: Dokument korrigieren und neu hochladen oder erzeugen. Lässt sich eine Abweichung nicht beheben, klären Sie mit dem Käufer eine **Änderung des Akkreditivs**, bevor Sie einreichen.
4. Befunde mit Hinweischarakter, die in Ordnung sind, können Sie mit **Begründung** als erfüllt bestätigen. Bei gleichen Hinweisen derselben Regel gibt es die Sammelbestätigung. Abweichungen werden nie gesammelt bestätigt.
5. Fehlt eine Angabe zum Akkreditiv (zum Beispiel Vorlagedatum oder Anzahl der Originale), erscheint sie gesammelt in einem gelben Kasten; ergänzen Sie sie dort, danach rechnet die Prüfung neu.

Die Karte ist eine **oberflächliche Vorprüfung**. Die Bank prüft endgültig und strenger. Prüfen Sie zusätzlich selbst die Punkte, die die Software nicht abdeckt (Original-Stempel, Unterschriften, Übereinstimmung mit der Ware).

## 6. Einreichungspaket erstellen

1. Klicken Sie in der Akte auf **Einreichungspaket**.
2. Der Dialog listet die Dokumente in der üblichen Einreichungsreihenfolge. Je Dokumentart ist das erste Dokument gewählt, mit der **geforderten Zahl an Originalen und Kopien** („3 Originale“, „Full Set 3/3“). Weitere Dokumente derselben Art sind abgewählt; wählen Sie bei Bedarf um.
3. Oben stehen die **offenen Abweichungen** der Vorprüfung. Sie sperren das Paket nicht, sollten aber vor der Einreichung geklärt sein.
4. Ergänzen Sie Datum, **Empfänger** (die Bank; vorbelegt), Ihre Referenz und Bemerkungen.
5. Optional speichern Sie das Datum als **Vorlagedatum** in der Akte.
6. Laden Sie das **ZIP** herunter. Es enthält das Begleitschreiben (`00-Einreichungsschreiben`, Word oder PDF) und die Dokumente als `01-…`, `02-…`.
7. Reichen Sie die Unterlagen innerhalb der **Vorlagefrist** und vor dem **Ablauf** des Akkreditivs bei der Bank ein. Originale gehen in der geforderten Zahl und Form in Papier oder auf dem von der Bank vorgegebenen Weg.

Das Begleitschreiben ist englisch, im Briefkopf Ihrer Firma, und listet Dokumente mit Nummer, Datum, Originalen und Kopien auf.

## 7. Nach der Einreichung

- Meldet die Bank **Unstimmigkeiten** (MT734 oder MT750), erscheinen sie in der Akte unter **Bankmitteilungen**. Klären Sie sie fristgerecht mit der Bank.
- Zahlung und erwarteter Geldeingang lassen sich mitführen ([Zahlungsstatus](zahlungsstatus.md), [Erwarteter Geldeingang](erwarteter-geldeingang.md)).
- Den **Fristenkalender** können Sie als Kalenderdatei (.ics) in Ihren eigenen Kalender übernehmen.

## Checkliste vor der Einreichung

- [ ] Meine Rolle in der Akte ist „Begünstigter“, offene Änderungen sind entschieden
- [ ] Alle verlangten Dokumente liegen vor, Original/Kopie richtig gekennzeichnet
- [ ] Rechnungssumme innerhalb Höchstbetrag, Währung gleich, Warenbeschreibung wie im Akkreditiv
- [ ] Daten (Rechnung, Verladung, Versicherung) liegen nicht nach spätestem Versand, Vorlagefrist und Ablauf sind gewahrt
- [ ] Akkreditivnummer und verlangte Angaben auf allen Dokumenten
- [ ] Unterschriften und Stempel vorhanden, Zahl der Originale stimmt
- [ ] Einreichungs-Check zeigt „Einreichungsbereit“ oder alle Abweichungen sind begründet geklärt
- [ ] Paket erstellt, Begleitschreiben geprüft

## Häufige Fragen

| Frage | Antwort |
|---|---|
| Kein Beschaffungsplan sichtbar | Rolle der Akte ist „Antragsteller“, oder das Profil des Mandanten ist nicht Corporate |
| Die Summe ist über dem Höchstbetrag | Positionen oder Preise anpassen; Toleranz steht in :39A: |
| Ein Dokument fehlt, ist aber hochgeladen | Dokumenttyp falsch gewählt: im Dokument korrigieren |
| Der Befund zeigt eine Abweichung, die ich für richtig halte | Quelle klären; nur Hinweise (nicht Abweichungen) lassen sich mit Begründung bestätigen |
| Ein Dokument soll als Word und PDF vorliegen | Format je Erzeugung wählen; ein zweiter Lauf erzeugt weitere Dokumente |
