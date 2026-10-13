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

## Noch offen (nächste Schritte)

Vorlaufzeiten pro Mandant einstellbar, manuelle Status („in Arbeit", „beantragt"), Warnungen per Kalender und E-Mail, Ampel in der LC-Liste und im
Cockpit, Rolle Exporteur/Importeur je Akte.
