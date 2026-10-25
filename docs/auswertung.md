# Auswertung (Diagramme im Cockpit)

Im Cockpit steht unter „Heute zu tun“ der Bereich **Auswertung** mit Diagrammen. Oben rechts wählen Sie den Zeitraum: **30 Tage**, **90 Tage** (Wochen) oder **12 Monate** (Monate). Fährt man mit der Maus (oder dem Finger) über ein Diagramm, zeigt eine Hinweisbox die Werte des Zeitraums.

* **Offene Akten:** Verlauf der Zahl offener Akkreditive. Daneben „Jetzt offen“ mit der Veränderung gegenüber dem Beginn des Zeitraums und die Zahl der Akten mit überschrittener Ablauffrist.
* **Offenes Volumen:** Verlauf des Volumens der offenen Akten je Währung (die drei größten Währungen, Auswahl rechts).
* **Eingänge und Abschlüsse:** Säulen je Woche oder Monat; die Veränderung steht im Vergleich zum gleich langen Zeitraum davor.
* **Fristen in den nächsten 12 Wochen:** Anstehende Ablauf-, Verladungs- und Wiedervorlagefristen der offenen Akkreditive, bei Corporate-Mandanten zusätzlich Ablauf und Entscheidungsfrist der Garantien. Überfällige Termine zählen in der ersten Woche.
* **Nur Bank-Profil:** **Prüfungen** (bestätigte Abweichungen und Prüfberichte je Zeitraum) und **Service-Level** (beendete SLA-Uhren, eingehalten oder zu spät).

Garantien zählen bei den Akten-Diagrammen nicht mit (sie haben ihr eigenes Cockpit unter „Garantien → Übersicht“); sie erscheinen nur bei den anstehenden Fristen. Wer eine Art von Akten nicht ansehen darf, sieht sie auch in den Diagrammen nicht.

## Woher die Zahlen kommen
Jede Akte hat seit Version 0.7.1 einen Eingangszeitpunkt (`received_at`) und, sobald sie abgeschlossen oder abgelaufen ist, einen Abschlusszeitpunkt (`closed_at`). Für bestehende Akten wurde der Eingang aus dem ersten Audit-Ereignis der Akte (sonst dem Ausstellungsdatum) und der Abschluss aus dem Ablaufdatum übernommen; der Verlauf davor ist daher eine Näherung, ab jetzt sind die Werte genau. „Offen am Tag X“ heißt: eingegangen am oder vor X und nicht vor Ende X abgeschlossen. Die Bank-Zahlen stammen aus dem Audit-Protokoll (`DOCUMENT_DISCREPANCY_CONFIRMED`, `DOCUMENT_CHECK_REPORT_CREATED`) und aus den SLA-Uhren.

## Schnittstelle
`GET /api/analytics?days=90` (7 bis 730 Tage; bis 120 Tage Wochen, darüber Monate) liefert die Reihen als JSON. Die Seite nutzt nur diese Schnittstelle.
