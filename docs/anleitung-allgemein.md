# Anleitung: Allgemeiner Teil (für alle Benutzer)

Diese Anleitung gilt für alle, die mit dem Corporate LC Manager arbeiten. Danach folgt je nach Aufgabe eine der beiden Rollenanleitungen:

- [Corporate: Dokumente erstellen und einreichen](anleitung-corporate-dokumente-erstellen.md)
- [Bank: Dokumente prüfen](anleitung-bank-dokumente-pruefen.md)

Stand: Version 0.7.2. Die Einrichtung (Server, Mandant, Benutzer) beschreibt der [Leitfaden zur Erstinstallation](erstinstallation.md).

## 1. Worum es geht

Der Corporate LC Manager führt Akkreditivdaten, Originaldokumente, SWIFT-Nachrichten, Änderungen, Fristen und Prüfentscheidungen in einer **LC-Akte** zusammen. Eine LC-Akte entsteht meist aus einer SWIFT-Nachricht (MT700) oder aus einem Avisierungsschreiben.

**Wichtig:** Die automatischen Prüfungen sind eine **Vorprüfung**. Ein grünes Ergebnis bestätigt nur die eingebauten Regeln. Es ist keine vollständige Dokumentenprüfung und keine Bankfreigabe.

## 2. Mandant und Profil

Ein **Mandant** ist ein getrennter Arbeitsbereich (zum Beispiel eine Firma oder eine Bank). Das **Profil** des Mandanten bestimmt, was Sie sehen:

| Profil | Typische Nutzer | Schwerpunkt |
|---|---|---|
| **Corporate** | Exporteur oder Importeur | Dokumente beschaffen, erzeugen, vorab prüfen, bei der Bank einreichen |
| **Bank** | Dokumentenprüfer der Bank | Eingereichte Dokumente ausführlich gegen das Akkreditiv prüfen |
| **Beides** | gemischte Mandanten | alle Funktionen |

Gibt es für Sie mehrere Mandanten, wählen Sie ihn bei der Anmeldung.

## 3. Anmeldung und Konto

1. Neue Benutzer erhalten eine **Einladung per E-Mail** (Link, 24 Stunden gültig, einmal verwendbar). Dort wählen Sie Ihr Passwort.
2. Melden Sie sich mit Benutzername und Passwort an. Bei mehreren Mandanten wählen Sie den Mandanten.
3. **Zwei-Faktor-Anmeldung (TOTP):** Administratoren müssen sie einrichten (QR-Code mit einer Authenticator-App scannen). Dabei werden **acht Notfallcodes einmalig** angezeigt. Bewahren Sie sie außerhalb der Anwendung auf; ohne Authenticator und Notfallcode kommen Sie nicht mehr hinein. Andere Benutzer können sie im Profil einrichten.
4. **Passwort vergessen?** Auf der Anmeldeseite führt der Link zu einem Reset. Benutzername und hinterlegte E-Mail-Adresse müssen zum selben Konto passen. Der Link gilt 30 Minuten und nur einmal; danach werden alte Sitzungen abgemeldet.
5. Nach mehreren Fehlversuchen meldet die Anwendung „429“. Warten Sie fünf Minuten.

Das Benutzermenü oben rechts bietet Profil, Bild, Passwort, TOTP und Abmelden. Im **Profil** wählen Sie Ihre Sprache (Deutsch oder Englisch) und pflegen Ihre E-Mail-Adresse.

## 4. Die Oberfläche

Das linke Menü öffnet zuerst die jeweilige Seite; von dort starten Sie bei Bedarf einen Dialog. Welche Punkte Sie sehen, hängt von Profil und Rechten ab.

| Menüpunkt | Wofür |
|---|---|
| **Cockpit / Meine Arbeit** | Ihre Aufgaben, Fristen, Wiedervorlagen, dringende Fälle („Heute zu tun“) |
| **LC-Akten** | Akten suchen, filtern, öffnen |
| **Dokumentenposteingang** | Hochgeladene Dateien, die noch keiner Akte zugeordnet sind |
| **SWIFT importieren / Importhistorie** | SWIFT-Nachrichten prüfen und übernehmen; Nachvollziehbarkeit |
| **Fristenkalender** | Ablauf, Verladetag, Vorlagefrist; Export als Kalenderdatei (.ics) |
| **Administration** | Firmenstammdaten, Vorlagen, Benutzer, Audit (nur mit Recht) |

Oben in der Kopfzeile stehen Suche und Benutzermenü. Die **Verbindungssicherheit** (Status der HTTPS-Verbindung) finden Sie im Menü.

## 5. Die LC-Akte

Eine Akte enthält:

- **Stammdaten:** Antragsteller, Begünstigter, Banken, Betrag und Währung, Ablaufdatum und -ort, spätester Versand, Dokumentenanforderungen (:46A:), Zusatzbedingungen (:47A:)
- **Dokumentenakte:** alle hochgeladenen und erzeugten Dokumente
- **Änderungen (Amendments)** aus MT707 mit Historie
- **Bankmitteilungen** (MT199/MT799, MT734, MT750, MT701)
- **Aufgaben, Chronik und Prüfentscheidungen**

Drei Referenzen werden getrennt geführt: die **Aktenreferenz** (eindeutig in der Anwendung), die **Referenz der eigenen Bank** und die **Referenz der Fremdbank**. „Ihre/unsere Referenz“ in einem Brief müssen Sie fachlich der richtigen Bank zuordnen.

### Akte anlegen

- **SWIFT-Import:** Menü „SWIFT importieren“, MT700-Text einfügen oder Datei wählen, Vorschau prüfen, importieren. Eine bereits vorhandene Referenz wird als Duplikat abgelehnt.
- **Aus einem Avisierungsschreiben:** Das PDF im Posteingang oder über „Neue LC-Akte aus Avisierungsschreiben“ einlesen. Erkannte Werte sind nur **Vorschläge**; Sie prüfen und bestätigen jedes Feld, bevor die Akte entsteht ([Avis einlesen](avis-einlesen.md)).

### Änderungen (MT707)

Eine importierte Änderung ist zunächst **offen** und ändert die Akte nicht. Erst **„Änderung annehmen“** (Recht „LC bearbeiten“) übernimmt sie in die gültige Fassung; **„Ablehnen“** lässt die Akte unverändert. Änderungen müssen in der Reihenfolge ihrer Nummern entschieden werden. Geprüft wird immer gegen die **geltende** Fassung.

## 6. Dokumente in der Akte

- **Hochladen:** PDF, XML, Bilder und Textdateien, höchstens 10 MB je Datei. Sammel- oder ZIP-Importe: höchstens 50 MB und 100 Dateien.
- **Dokumentenposteingang:** Dateien werden gesammelt hochgeladen. Die Anwendung macht **Zuordnungsvorschläge** (Akte, Dokumenttyp, Original oder Kopie); Sie bestätigen oder korrigieren. „Bearbeitungsstand speichern“ legt einen Zwischenstand ab, „Metadaten bestätigen & lernen“ speichert zusätzlich Ihre Bestätigung als Lernbeispiel.
- **Lesen der Dokumente:** Digitale PDFs nutzen ihre Textebene, Scans werden per OCR gelesen. Scans mit schlechter Qualität sind ein manueller Prüffall.
- **Original oder Kopie:** Kennzeichnen Sie jedes Dokument richtig. Die Prüfung vergleicht die verlangte Zahl von Originalen und Kopien mit diesen Angaben. Das ist **keine** Prüfung der physischen Originale oder der Echtheit.
- **Identische Dateien:** Ein Vergleich zeigt byteidentische Dateien an; nichts wird automatisch gelöscht oder zusammengeführt.

## 7. Rechte und Vier-Augen-Prinzip

Ihre Rechte hängen an Ihrer Rolle (Administrator, Bearbeiter, Betrachter oder eigene Rollen). Fehlt Ihnen ein Knopf, fehlt meist ein Recht; wenden Sie sich an den Mandantenadministrator ([Benutzer & Rechte](benutzer-und-rechte.md)).

Das **Vier-Augen-Prinzip** gilt für sensible Schritte: Wer einen Dokumententwurf erstellt hat, darf ihn nicht selbst freigeben. Bei hohen Beträgen können mehrere Freigeber nötig sein. Ebenso braucht ein Sanktionstreffer, der als „Falsch-positiv“ vorgemerkt ist, eine zweite Freigabe.

## 8. Fristen und Aufgaben

- Ablauf, spätester Versand und Vorlagefrist erscheinen im **Fristenmonitor** und **-kalender**.
- „Meine Arbeit“ zeigt, was Ihnen zugewiesen ist. Aufgaben können automatisch entstehen (zum Beispiel wenn ein Dokument knapp wird).
- Für knappe Fristen kann das System eine tägliche Zusammenfassung per E-Mail senden, sofern der Mailversand eingerichtet ist.

## 9. Nachvollziehbarkeit

Das **Audit-Protokoll** hält wichtige Aktionen fest (wer, wann, was; teils mit Vorher-/Nachher-Wert). Prüfentscheidungen, übersprungene Schritte und Begründungen erscheinen dort. Das Protokoll ist kein externes, unveränderliches Archiv; Zugriff und Sicherung regelt der Betrieb.

## 10. Hilfe und Grenzen

| Frage | Antwort |
|---|---|
| Ein Knopf fehlt | Recht oder Profil fehlt: Administrator fragen |
| Eine Datei wird abgelehnt | Größe, Format, Archivinhalt prüfen (Grenzen in Abschnitt 6) |
| Der Anmeldelink funktioniert nicht | Einladung ist 24 Stunden gültig; Administrator kann sie erneut senden |
| Der Authenticator ist verloren | Notfallcode verwenden; sonst den Betrieb bitten |
| Ein Wert wurde falsch erkannt | Im Training oder im Posteingang korrigieren und bestätigen |

Die Software ersetzt weder die fachliche Dokumentenprüfung noch ein zertifiziertes Sanktions-Screening.
