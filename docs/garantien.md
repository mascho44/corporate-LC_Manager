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

## Geplant (weitere Bausteine)
Oberfläche (Seite „Garantien“ und Block in der Akte), Linien (Avallinien) mit Auslastung, Beauftragung per E-Mail mit Statusverfolgung, Einlesen von Mustertexten und Textabgleich.
