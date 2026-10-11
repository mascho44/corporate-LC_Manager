# KI-Unterstützung (optional, anbieterunabhängig)

Standardmäßig **aus**. Sie läuft nur, wenn der Betreiber einen KI-Dienst konfiguriert **und** der Mandant sie unter „Mandant → KI-Vorschläge“ einschaltet.

## Konfiguration pro Mandant (Oberfläche)
Unter **Mandant → KI-Vorschläge** (Recht `SETTINGS_MANAGE`) hinterlegt jeder Mandant seinen eigenen Dienst: Anbieter (Mistral, OpenAI, eigener OpenAI-kompatibler Dienst, Ollama lokal), Adresse, Modell, API-Schlüssel und Zeitlimit. **Verbindung testen** schickt einen erfundenen Mini-Text und prüft, ob der Dienst auswertbares JSON liefert; es verlassen keine Mandantendaten das System. Danach schaltet der Mandantenadmin die Funktion ein.

- Der **API-Schlüssel** wird verschlüsselt gespeichert (AES-256-GCM, die Zeilen-ID ist authentifiziert) und nie angezeigt oder von der Schnittstelle zurückgegeben; ein leeres Feld beim Speichern behält den vorhandenen Schlüssel. Ändert sich Anbieter oder Host, muss der Schlüssel neu eingegeben werden (ein Schlüssel reist nie an einen anderen Host). **Schlüssel entfernen** schaltet die KI für den Mandanten aus.
- Änderungen, Tests und das Ein-/Ausschalten stehen im Audit (ohne Schlüssel).

## Konfiguration (Betreiber)
| Variable | Bedeutung |
|---|---|
| `LC_AI_ALLOWED_HOSTS` | Kommagetrennte Liste erlaubter Anbieter-Hosts, z. B. `api.mistral.ai`. **Ohne Eintrag kann kein Mandant einen externen Dienst konfigurieren.** Alles andere wird abgelehnt (Schutz vor Anfragen an interne Dienste) |
| `LC_AI_ENCRYPTION_KEY` | Schlüssel (mind. 32 Zeichen) zum Verschlüsseln der Mandanten-API-Schlüssel; fehlt er, wird `EBICS_ENCRYPTION_KEY` verwendet |
| `LC_AI_ALLOW_LOCAL` | `true` erlaubt lokale Endpunkte (Ollama); nur bewusst setzen |
| `LC_AI_ENABLED`, `LC_AI_PROVIDER`, `LC_AI_BASE_URL`, `LC_AI_MODEL`, `LC_AI_API_KEY`, `LC_AI_TIMEOUT_SECONDS` | Optionaler **Standard des Betreibers** für Mandanten, die nichts Eigenes hinterlegen (`LC_AI_PROVIDER`: `ollama` oder `openai-compatible`) |

Die Regeln für Adressen: nur HTTPS auf Port 443, keine Zugangsdaten oder Parameter in der Adresse, der Host muss auf der Liste stehen, und er darf nicht auf interne, private oder Link-Local-Adressen auflösen (geprüft beim Speichern und vor jeder Nutzung; Weiterleitungen werden nicht verfolgt). Wird ein Host aus der Liste entfernt, wirkt das sofort für alle Mandanten. Externe Dienste brauchen einen Schlüssel; er steht nur im Authorization-Header und wird nie protokolliert. Ein Anbieter mit anderer Schnittstelle wird als zusätzliche Klasse hinter `LlmClient` angebunden.

## Was die KI tut – und was nicht
- **Vorschläge je Dokument** (Dialog „Dokumentangaben“ → *KI-Vorschläge holen*): Pro Dokumenttyp werden nur passende Felder abgefragt (z. B. bei der Rechnung keine Konsignatarin). Nur Felder, die weder gefüllt noch von der Regel-Erkennung vorgeschlagen sind.
- **Prüfung jeder Antwort:** Jeder Wert braucht ein **wörtliches Zitat**, das im Dokument steht und den Wert stützt (Text enthalten; Zahlen und Daten gleich nach Normalisierung, z. B. 12.345,60 = 12345.6). Alles andere wird verworfen. Das verhindert erfundene Werte, nicht jede falsche Zuordnung – deshalb immer prüfen.
- **KI-Hinweise** (Prüfung → *KI-Hinweise*): unverbindliche Anregungen zu Unterschieden zwischen den Dokumenten, jeweils mit Zitat als Beleg; Hinweise ohne belegbares Zitat werden verworfen. Sie fließen **nie** in die Regelprüfung ein, die Prüfentscheidung bleibt deterministisch.
- Nichts wird automatisch gespeichert; KI-Vorschläge sind nicht Teil von „Alle übernehmen“.
- Jede Anfrage wird im Audit vermerkt (Anbieter, Modell, Anzahl); ebenso das Ein- und Ausschalten.

## Vor dem Einsatz mit echten Daten klären
Bei externen Diensten verlassen Dokumenttexte das Haus. Vorab: Auftragsverarbeitungsvertrag, Zero Data Retention bzw. Ausschluss von Training mit API-Daten, Standort der Verarbeitung (EU), Freigabe durch Datenschutz/Compliance (Bankgeheimnis, Mandantentrennung). Lokale Modelle (Ollama, vLLM) vermeiden das, brauchen aber passende Hardware (kleine Modelle lesen verrauschte Scans deutlich schlechter).

## Schnittstellen
`GET /api/ai/status`, `PUT /api/ai/settings`, `PUT /api/ai/config`, `DELETE /api/ai/config/key`, `POST /api/ai/test` (Recht `PERM_SETTINGS_MANAGE`), `POST /api/lcs/{lcId}/documents/{id}/rule-facts/ai-suggestions` und `POST /api/lcs/{lcId}/ai-hints` (Recht `PERM_DOCUMENT_UPLOAD`).

## Beispiel für die `.env` (Mistral)
```
LC_AI_ALLOWED_HOSTS=api.mistral.ai
```
Mehr braucht der Betreiber nicht (der Verschlüsselungsschlüssel kommt bei vorhandenem `EBICS_ENCRYPTION_KEY` automatisch daher). Schlüssel und Modell tragen die Mandantenadmins in der Oberfläche ein.

## Test vor dem Produktiveinsatz
1. Zuerst mit synthetischen oder geschwärzten Dokumenten.
2. Je Dokumenttyp zehn Dokumente: Wie viele Vorschläge stimmen, wie viele sind falsch zugeordnet, wie viele fehlen?
3. Erst bei guter Trefferquote und freigegebenem Datenschutz mit echten Dokumenten.
