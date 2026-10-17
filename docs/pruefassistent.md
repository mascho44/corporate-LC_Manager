# Prüfungsassistent: geführte Prüfung in sieben Schritten

Stand: 17.10.2026. Der Assistent führt durch die Prüfung einer Akte in einer festen fachlichen Reihenfolge. Er ersetzt den [Prüfmodus](pruefmodus.md) nicht, sondern ruft ihn an der richtigen Stelle auf. Start: in der Akte **„Prüfungsassistent"** (Recht „Dokumente prüfen"; ohne dieses Recht ist er nur lesbar).

## Die sieben Schritte
| Schritt | Was geprüft wird |
|---|---|
| 1 Akte und Bedingungen | Parteien, Betrag, Ablauf und Ort, spätester Versand, geforderte Dokumente; „Akte bearbeiten". |
| 2 Vollständigkeit | Vorliegende Dokumente mit Original/Copy-Kennzeichnung; Befunde zu fehlenden Dokumenten, Zahl der Originale und Kopien, Full Set. |
| 3 Fehlende Angaben | Regeln, die an einer fehlenden LC-Angabe scheitern; das Vorlagedatum direkt speichern, andere Angaben in den LC-Prüfdaten erfassen. |
| 4 Compliance | Sanktionen, Embargo, Exportkontrolle, HS-Code; Treffer im Prüfmodus entscheiden (Falsch-positiv mit Vier-Augen-Freigabe). |
| 5 Dokumente | Übersicht je Dokument (offen / erledigt) und Sprung in den dokumentweisen Prüfmodus; nach dem Schließen wird neu geladen. |
| 6 Querprüfungen und Fristen | Konsistenz über Dokumente (Betrag, Menge, Daten, Zusatzbedingungen) sowie Ablauf, spätester Versand und Vorlagedatum mit „in N Tagen". |
| 7 Abschluss | Ergebnis (entschieden / offen / blockierend), übersprungene Schritte mit Begründung, Unstimmigkeitsmeldung, Abschlussvermerk, Export der Prüfakte. |

Welcher Befund in welchen Schritt gehört, bestimmt die Reihenfolge Fehlende Angabe → Compliance → Vollständigkeit → Querprüfung (auch alles ohne einzelnes Dokument) → Dokument (`review-steps.js`, `stepOf`).

## Bedienung
* Links die Schrittleiste (✓ erledigt, ↷ übersprungen, rote Zahl = offene Befunde), oben der Fortschritt, rechts der Inhalt des Schritts mit Erklärung und den offenen Befunden (bis zu zwölf, der Rest im Prüfmodus).
* **Schritt erledigt ✓** ist nur möglich, wenn im Schritt nichts mehr offen ist. Sonst: **Überspringen …** mit Pflichtbegründung (mindestens 3 Zeichen); der Grund steht im Assistenten, im Abschlussschritt und im Audit-Protokoll. **Wieder öffnen** nimmt einen Eintrag zurück.
* **Zurück / Weiter** wechseln nur die Ansicht, ohne etwas zu speichern. Der Assistent springt zum ersten Schritt ohne Eintrag.
* Der Abschlussvermerk („Dokumentenprüfung abgeschlossen (Prüfungsassistent) …", Recht „LC bearbeiten") ist erst möglich, wenn nichts mehr offen ist. Der Status der Akte ändert sich dadurch nicht.

## Gespeicherter Fortschritt
Der Fortschritt wird **je Akte** gespeichert (Tabelle `lc_review_step`, V120): Schritt, Status (`DONE`/`SKIPPED`), Begründung, wer und wann. So kann eine Kollegin an derselben Stelle weitermachen. REST: `GET /api/lcs/{id}/review-steps`, `PUT …/{schritt}` mit `{status, reason}`, `DELETE …/{schritt}` (Recht „Dokumente prüfen"); Audit `REVIEW_STEP_DONE`, `REVIEW_STEP_SKIPPED`, `REVIEW_STEP_REOPENED`. Ein erledigter Schritt bleibt erledigt, auch wenn später neue Befunde entstehen (zum Beispiel nach einem weiteren Upload); der Schrittzähler zeigt sie als offene Zahl am Schritt, und der Abschluss meldet sie.
Die Schritte sind im Code fest (keine Konfiguration). Der Fortschritt wird mit der Akte bzw. dem Mandanten gelöscht.

## Noch offen
Einstieg aus der Aktenliste, eine Checkliste mit Abhaken einzelner Punkte je Schritt, Hinweise zu Fristen nach Arbeitstagen und Feiertagen.
