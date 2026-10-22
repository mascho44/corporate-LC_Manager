# SLAs (Bank-Profil)

Ein SLA beschreibt, wie lange eine Tätigkeit dauern darf. Die Regeln gelten je Mandant und stehen nur Mandanten mit Bank-Profil zur Verfügung
(Einstellungen → **SLAs**, Recht „Einstellungen verwalten“).

## Regel
- **Name** und **Tätigkeit**: Vorlagen für Erstbearbeitung der Akte, Dokumentenprüfung (Standard 5 Bankarbeitstage), Änderung (MT707) bearbeiten
  und Aufgabe erledigen, oder eine eigene Tätigkeit.
- **Start-Ereignisse** und **Stopp-Ereignisse**: Audit-Aktionen an der Akte (zum Beispiel `DOCUMENT_UPLOADED` → `DOCUMENT_CHECK_REPORT_CREATED`).
  Es müssen keine anderen Module etwas melden; ausgewertet wird das Audit-Protokoll der Akte.
- **Zielzeit** in **Bankarbeitstagen** (Montag bis Freitag ohne die bundesweiten Feiertage Neujahr, Karfreitag, Ostermontag, 1. Mai,
  Christi Himmelfahrt, Pfingstmontag, 3. Oktober, 1. und 2. Weihnachtstag; Feiertage einzelner Bundesländer zählen nicht) und **Warnschwelle** in Prozent.

## Auswertung
Ein Start-Ereignis öffnet eine Uhr, solange keine offen ist; das nächste Stopp-Ereignis schließt sie. Fällig ist der Tag, der die Zielzeit in Bankarbeitstagen
nach dem Starttag liegt (der Starttag zählt nicht). Laufend: **im Plan**, **knapp** (ab der Warnschwelle) oder **verletzt** (nach dem Fälligkeitstag).
Beendet: **eingehalten** oder **zu spät erledigt**. In der Aktenübersicht steht der Block „SLA“ mit allen Uhren der Akte.

## Grenzen (Stand 0.5.1)
- Ereignisse, die nicht an einer Akte protokolliert werden (zum Beispiel der Eingang im Posteingang vor der Zuordnung, Sanktionstreffer), sind noch keine SLA-Ereignisse.
- Eskalation über die Gruppeninbox und Filter in der Aktenliste folgen.
