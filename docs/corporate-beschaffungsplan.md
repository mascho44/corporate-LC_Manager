# Corporate: Beschaffungsplan

Für Mandanten mit Corporate-Profil zeigt die Übersicht einer LC-Akte oben den **Beschaffungsplan**:
was die LC verlangt, was schon hochgeladen ist, was noch fehlt, und eine Ampel.

`GET /api/lcs/{id}/procurement-plan` liefert je geforderter Dokumentenbedingung (:46A:) den erkannten Dokumenttyp, ob ein
Dokument dieses Typs vorliegt, den Zieltermin und das Risiko.

## Fristlogik

* Das wichtigste Datum ist der **späteste Versandtermin (:44C:)**. Bis dahin muss die Ware verladen sein; das Transportdokument
  (B/L, AWB, CMR, Sea Waybill, Kurier-/Posteinlieferungsbeleg) ist an diesem Tag fällig.
* Alle anderen Dokumente werden in **Arbeitstagen rückwärts** vom spätesten Versandtermin gerechnet (Samstag/Sonntag zählen nicht, Feiertage
  bleiben unberücksichtigt). Richtwerte: Handelsrechnung 2, Packliste/Weight List 3, Versicherung 3, Begünstigtenzertifikat 2, Wechsel 2,
  Ursprungszeugnis 7, Inspektions-/Qualitätszertifikat 10, übrige 5 Arbeitstage.
* Ampel je offenem Dokument: **überfällig** (Zieltermin vorbei), **kritisch** (höchstens 3 Tage), **bald fällig** (höchstens 10 Tage), **im Plan**.
  Vorhandene Dokumente sind grün. Fehlt der Versandtermin oder ist die Anforderung nicht klassifiziert, steht die Ampel auf „unklar".
* Die Gesamtampel oben zeigt den Versandtermin: grün, sobald ein Transportdokument vorliegt.

Der Plan ist eine Planungshilfe und ersetzt weder die Prüfung noch die Einreichung bei der Bank. „Vorhanden" bedeutet nur, dass ein Dokument
dieses Typs hochgeladen wurde.

## Vorlaufzeiten und Stand

* **Vorlaufzeiten pro Mandant:** Mit dem Recht „Einstellungen verwalten" öffnet der Knopf *Vorlaufzeiten* im Plan eine Tabelle mit den Arbeitstagen
  je Dokumentart (`GET/PUT/DELETE /api/procurement/lead-times`). Geänderte Werte gelten für alle LC-Akten des Mandanten, „Standard" stellt den
  Richtwert wieder her. Transportdokumente sind fest am Versandtag fällig. Änderungen werden im Audit protokolliert.
* **Manueller Stand:** Mit dem Recht „LC bearbeiten" lässt sich je fehlendem Dokument *Nicht begonnen*, *In Arbeit* oder *Beantragt / bestellt* setzen,
  dazu eine Notiz (`PUT /api/lcs/{id}/procurement-status`). Der Stand ändert die Ampel nicht, er zeigt nur, wer woran arbeitet. „Vorhanden" kommt weiter
  allein aus dem Upload. Auch das wird protokolliert.

## Warnungen

* **Kalender:** Für Corporate-Mandanten erscheinen die Zieltermine fehlender Dokumente im Fristenkalender und im iCal-Export
  („Beschaffung: Certificate of Origin bereitstellen", Transportdokumente als „(Versand)").
* **Aufgaben:** Unter *Automatische Aufträge* gibt es den Auslöser *Dokument für die Beschaffung wird knapp*. Ist er für ein Team aktiv, entsteht
  für jedes fehlende Dokument, dessen Zieltermin innerhalb des eingestellten Vorlaufs liegt (oder überschritten ist), eine Teamaufgabe
  („Beschaffung: LC-Referenz – Dokument bis TT.MM.JJJJ"). Sie schließt sich selbst, sobald das Dokument hochgeladen ist oder die LC abgeschlossen wird.
  Der Lauf erfolgt stündlich, höchstens eine Aufgabe je LC, Dokument und Zieltermin.

* **E-Mail:** Mit demselben Auslöser versendet das System einmal täglich (ab 7 Uhr, beim ersten stündlichen Lauf) eine Zusammenfassung an jeden Empfänger
  mit allen Dokumenten, die überfällig, kritisch oder bald fällig sind. Empfänger ist die zugewiesene Person der LC; ohne Zuweisung die Mitglieder des Teams der Regel.
  Gibt es nichts zu melden, kommt keine Mail. Voraussetzung sind `SMTP_ENABLED=true`, `SMTP_FROM` und ein funktionierender SMTP-Zugang; sonst wird nichts gesendet.
  Je Mandant und Tag wird höchstens einmal gesendet (Tabelle `procurement_digest_log`, vor dem Versand geschrieben).

## Noch offen (nächste Schritte)

Ampel in der LC-Liste und im
Cockpit, Rolle Exporteur/Importeur je Akte.
