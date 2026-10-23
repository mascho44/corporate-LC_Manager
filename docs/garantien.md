# Garantien (Kundenseite, Corporate-Profil)

LCM führt die Garantien, die das Unternehmen von einer Bank für einen Begünstigten ausstellen lässt (Anzahlung, Vertragserfüllung, Gewährleistung, Bietung, Zahlung, Zoll, Miete, Sonstige). Die Bankseite ist **nicht** Gegenstand; die Funktion steht nur Mandanten mit Corporate-Profil zur Verfügung.

## Modell
Eine Garantie ist eine **Akte** (Art `GUARANTEE`, Rolle Auftraggeber) mit zusätzlichen Angaben (`guarantee_detail`, Migration V143). Dadurch gelten Dokumente, Verlauf, Aufgaben und Audit-Protokoll wie bei jeder Akte. Eine per MT760 eingelesene Garantie ist ohne Angaben „aktiv“ und lässt sich ergänzen.

| Angabe | Bedeutung |
|---|---|
| Art | `ADVANCE_PAYMENT`, `PERFORMANCE`, `WARRANTY`, `BID`, `PAYMENT`, `CUSTOMS`, `RENT`, `OTHER` |
| Referenz, Bank, Begünstigter, Währung, Betrag | Pflicht; die Referenz ist je Mandant eindeutig (auch gegenüber Akkreditiv-Akten) |
| Ausstellung, Gültig ab, **Ablauf** | Ablauf Pflicht, außer bei automatischer Verlängerung ohne festes Ende |
| **Inanspruchnahme bis** | Ende der Frist, in der der Begünstigte noch Ansprüche stellen kann (nicht vor dem Ablauf) |
| **Automatische Verlängerung** und **Entscheidungsfrist (Tage vor Ablauf)** | Wann entschieden werden muss, ob die Garantie verlängert oder zurückgegeben wird |
| Grundgeschäft, Aktenzeichen des Begünstigten | Freitext |
| Status | `REQUESTED` (beantragt), `ACTIVE` (aktiv), `RETURNED` (zurückgegeben), `CLAIMED` (in Anspruch genommen), `EXPIRED` (abgelaufen) |

Wechselt eine Garantie auf Zurückgegeben, In Anspruch genommen oder Abgelaufen, wird die Akte geschlossen und es gibt keine Fristerinnerungen mehr; „aktiv“ öffnet sie wieder.

## Fristen
Für offene Garantien erscheinen in den Fristen der Akte und im Fristenkalender (Art „Garantie“): **Ablauf der Garantie**, **Ende der Frist für die Inanspruchnahme** und **Entscheiden: Verlängerung … oder Rückgabe** (Ablauf minus Entscheidungsfrist; bei automatischer Verlängerung mit dem Hinweis, dass sich die Garantie sonst verlängert). Die Aufgaben der Fristenüberwachung (Gruppeninbox) erfassen diese Termine wie den Ablauf.

## Schnittstelle
`GET /api/guarantees` (Liste: offene zuerst, nach Ablauf; „läuft in höchstens 90 Tagen ab“ ist gekennzeichnet), `GET /api/guarantees/options`, `GET /api/guarantees/{id}`, `POST /api/guarantees`, `PUT /api/guarantees/{id}`, `POST /api/guarantees/{id}/status`. Lesen für alle Angemeldeten, Schreiben mit dem Recht „LC-Akten bearbeiten“. Audit: `GUARANTEE_CREATED`, `GUARANTEE_UPDATED`, `GUARANTEE_STATUS_CHANGED`.

## Oberfläche
- **Seite „Garantien“** (Menü, nur Corporate-Profil): Liste mit Art, Status, Bank, Begünstigtem, Betrag, Ablauf, Restlaufzeit, Entscheidungstermin und Hinweis auf automatische Verlängerung; Ansichten *Offene*, *Laufen in 90 Tagen ab*, *Erledigte*, *Alle* und Suche (Referenz, Bank, Begünstigter, Grundgeschäft). *Garantie anlegen* öffnet das Formular (Recht „LC-Akten bearbeiten“); *Akte öffnen* springt in die Akte.
- **Block „Garantie“ in der Akte** (nur Akten der Art Garantie): Status, Art, Bank, Begünstigter, Betrag, Ablauf, Frist für die Inanspruchnahme, Entscheidungstermin, Grundgeschäft; Knöpfe *Garantie bearbeiten* und *Status ändern* (Datum und Hinweis). Fehlen bei einer eingelesenen Garantie die Angaben, steht dort ein Hinweis.

## Linien (Avallinien)
Auf der Seite „Garantien“ steht oben **Linien und Auslastung** (Migration V144, `/api/credit-lines`). Eine Linie ist der Rahmen, den eine Bank dem Unternehmen eingeräumt hat: Name, Bank, optional BIC, Währung, Betrag, optional eine **Garantieart** (sonst alle Arten), optional „Gültig bis“, Warnschwelle (Standard 80 %), aktiv, Hinweis.
- **Zuordnung:** Jede **offene** Garantie (beantragt oder aktiv) in der Währung der Linie belegt die Linie ihrer Bank: zuerst eine Linie für ihre Art, sonst eine für alle Arten; so wird nie doppelt gezählt. Die Bank wird über die **erste Zeile** des Banknamens (ohne Groß-/Kleinschreibung und Satzzeichen) oder über die **BIC** der Linie im Banktext der Garantie erkannt – die Bank sollte bei Linie und Garantie gleich geschrieben sein. Zurückgegebene, in Anspruch genommene und abgelaufene Garantien geben die Linie frei.
- **Anzeige:** Balken mit belegt, frei, Prozent, Zahl der Garantien; Zustand *im Rahmen*, *knapp* (ab der Warnschwelle) oder *überschritten*; Kennzeichen *abgelaufen* und *inaktiv*. Offene Garantien, die zu keiner Linie passen, stehen unter „Ohne Linie“.
- **Prüfung beim Erfassen:** Im Garantieformular zeigt ein Hinweis, ob die Garantie in die Linie passt (frei, Rest danach) oder sie überschreitet; eine Sperre gibt es nicht. Beim Ändern wird die Garantie selbst nicht doppelt gezählt. Schnittstelle: `GET /api/credit-lines/check`.
- **Rechte:** Anlegen, Ändern und Löschen mit dem Recht „Einstellungen verwalten“, Ansehen für alle. Audit: `CREDIT_LINE_CREATED`, `_UPDATED`, `_DELETED`.
- Nicht enthalten: Beträge in anderen Währungen werden nicht umgerechnet; eine Garantie in einer Währung ohne Linie steht unter „Ohne Linie“.

## Beauftragung (der einfache Weg)
Eine Garantie kann mit dem Status *beantragt* angelegt werden; in ihrer Akte steht dann der Block **Antrag bei der Bank** (Migration V145, `/api/guarantees/{id}/request`).
1. **Antrag vorbereiten:** *Antrag bearbeiten* (Empfänger, Unser Zeichen, gewünschter Wortlaut, Bemerkungen, Antwort erwartet bis).
2. **Als Word erzeugen:** *Antrag als Word (Deutsch)* oder *(Englisch)* erstellt einen Brief mit Briefkopf aus dem Firmenprofil, den Angaben der Garantie (Art, Begünstigter, Betrag, Gültigkeit, Inanspruchnahme, Verlängerung, Grundgeschäft), dem Wortlaut und einer Unterschriftszeile. LCM **versendet nichts**: Sie unterschreiben und senden den Antrag selbst (E-Mail, Portal, Post).
3. **Status verfolgen:** *Antragsstatus ändern*: Entwurf → gesendet → Rückfrage der Bank → zugesagt → ausgestellt, oder abgelehnt / zurückgezogen (mit Datum, Hinweis und Referenz der Bank). Der erste Sendetermin bleibt erhalten.
   - **Ausgestellt** macht die Garantie **aktiv**. **Abgelehnt** oder **zurückgezogen** schließt sie als *nicht zustande gekommen* (Status der Garantie `CANCELLED`, keine Erinnerungen mehr). Ein abgeschlossener Antrag lässt sich nicht mehr ändern.
4. **Frist:** Ist der Antrag gesendet oder mit Rückfrage offen und „Antwort erwartet bis“ gesetzt, erscheint der Termin „Antwort der Bank auf den Garantieantrag erwartet“ in den Fristen und im Fristenkalender.
Audit: `GUARANTEE_REQUEST_SAVED`, `GUARANTEE_REQUEST_STATUS_CHANGED`, `GUARANTEE_REQUEST_DOCUMENT_CREATED`. Schreiben mit dem Recht „LC-Akten bearbeiten“.

## Geplant (weitere Bausteine)
Versand aus LCM per E-Mail oder EBICS, Einlesen von Mustertexten und Textabgleich (siehe Issue 509).
