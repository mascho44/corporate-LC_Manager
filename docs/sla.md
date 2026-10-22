# SLAs (Bank-Profil)

Ein SLA beschreibt, wie lange eine Tätigkeit dauern darf. Die Regeln gelten je Mandant und stehen nur Mandanten mit Bank-Profil zur Verfügung
(Einstellungen → **SLAs**, Recht „Einstellungen verwalten“).

## Regel
- **Name** und **Tätigkeit**: Vorlagen für Erstbearbeitung der Akte, Dokumentenprüfung (Standard 5 Bankarbeitstage), Unstimmigkeitsmeldung erstellen (Abweichung bestätigt → Meldung erstellt), Änderung (MT707) bearbeiten,
  Aufgabe erledigen und Dokument im Posteingang zuordnen, oder eine eigene Tätigkeit.
- **Start-Ereignisse** und **Stopp-Ereignisse**: Audit-Aktionen an der Akte (zum Beispiel `DOCUMENT_UPLOADED` → `DOCUMENT_CHECK_REPORT_CREATED`).
  Es müssen keine anderen Module etwas melden; ausgewertet wird das Audit-Protokoll der Akte.
- **Zielzeit** in **Bankarbeitstagen** (Montag bis Freitag ohne die bundesweiten Feiertage Neujahr, Karfreitag, Ostermontag, 1. Mai,
  Christi Himmelfahrt, Pfingstmontag, 3. Oktober, 1. und 2. Weihnachtstag; Feiertage einzelner Bundesländer zählen nicht) und **Warnschwelle** in Prozent.

## Auswertung
Ein Start-Ereignis öffnet eine Uhr, solange keine offen ist; das nächste Stopp-Ereignis schließt sie. Fällig ist der Tag, der die Zielzeit in Bankarbeitstagen
nach dem Starttag liegt (der Starttag zählt nicht). Laufend: **im Plan**, **knapp** (ab der Warnschwelle) oder **verletzt** (nach dem Fälligkeitstag).
Beendet: **eingehalten** oder **zu spät erledigt**. In der Aktenübersicht steht der Block „SLA“ mit allen Uhren der Akte.

## Eskalation und Liste
- Je Regel lässt sich ein **Team** für die Eskalation wählen. Stündlich entsteht für jede **verletzte laufende Uhr** genau ein Auftrag in der Gruppeninbox dieses Teams
  („SLA verletzt: Referenz – Regel (fällig …)“, höchstens einmal je Uhr).
- In der Aktenliste zeigt jede Akte mit knapper oder verletzter Uhr das Kennzeichen „SLA knapp“ / „SLA verletzt“; der Filter „SLA“ erscheint, sobald es solche Akten gibt.

## Posteingang
Die Tätigkeit „Dokument im Posteingang zuordnen“ gehört zu Dokumenten, nicht zu Akten: Die Uhr läuft ab dem Eingang eines offenen Posteingangsdokuments, bis es einer Akte zugeordnet ist. Auf der SLA-Seite stehen die knappen und überfälligen Dokumente; bei einem Eskalationsteam entsteht für ein überfälliges Dokument ein Auftrag in der Gruppeninbox.

## Grenzen (Stand 0.5.1)
- Sanktionstreffer sind noch keine SLA-Ereignisse: Treffer entstehen aus der Prüfung und werden nicht als Ereignis protokolliert.
- Die Auswertung liest die Audit-Ereignisse der letzten 500 Tage.
