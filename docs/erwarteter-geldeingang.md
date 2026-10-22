# Erwarteter Geldeingang in den Fristen

Aus der Zahlungsklausel des Akkreditivs (`:42C:`, `:42P:`, `:42M:`) schätzt die Anwendung, wann Geld zu erwarten ist. Der Termin steht als **„Voraussichtliche Fälligkeit“** in den Fristen der Akte und im Fristenkalender (Art „Fälligkeit“).
Er ist immer eine **Schätzung** („nicht gesichert“) mit dem Hinweis, die Zahlungsbedingungen fachlich zu prüfen. Es wird nichts geraten: Fehlt das Datum, von dem die Frist läuft, entsteht kein Termin.

| Klausel (Beispiele) | Fristbeginn | Voraussetzung |
|---|---|---|
| `60 DAYS AFTER B/L DATE`, `… FROM DATE OF SHIPMENT`, `60 TAGE NACH B/L-DATUM` | Datum des Transportdokuments (Konnossement, Luftfrachtbrief, CMR) | Transportdokument mit Datum in der Akte |
| `30 DAYS AFTER INVOICE DATE`, `30 TAGE NACH RECHNUNGSDATUM` | Datum der Handelsrechnung | Rechnung mit Datum in der Akte |
| `90 DAYS AFTER SIGHT`, `30 DAYS AFTER PRESENTATION …` | Datum der Einreichung der Dokumente | „Geprüftes Datum der Dokumentenvorlage“ (wird beim Status „Dokumente eingereicht“ gesetzt oder im LC-Prüfkontext eingetragen) |
| `AT SIGHT`, `BEI SICHT` | Einreichung, plus höchstens **5 Bankarbeitstage** Prüfzeit (UCP 600 Art. 14); Titel „Späteste Zahlung bei Sicht“, die Bank kann früher zahlen | Datum der Einreichung |

- **Tage:** Standard sind Kalendertage. Steht `banking days`, `business days`, `working days`, `Bankarbeitstage` oder `Werktage` in der Klausel, zählen Bankarbeitstage (Montag bis Freitag ohne bundesweite Feiertage, wie bei den [SLAs](sla.md)).
- **Nicht erkannt:** andere Fristbeginne (zum Beispiel nach Akzept), Zahlung nach festem Datum und Teilzahlungen. Dort entsteht kein Termin.
- **Tatsächlicher Zahlungseingang:** wird nicht erfasst (siehe Issue 45, braucht Zahlungsdaten).
