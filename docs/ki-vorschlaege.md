# KI-Unterstützung (optional, anbieterunabhängig)

Standardmäßig **aus**. Sie läuft nur, wenn der Betreiber einen KI-Dienst konfiguriert **und** der Mandant sie unter „Mandant → KI-Vorschläge“ einschaltet.

## Konfiguration (Betreiber)
| Variable | Bedeutung |
|---|---|
| `LC_AI_ENABLED` | `true` schaltet die Anbindung für die Installation ein (Standard `false`) |
| `LC_AI_PROVIDER` | `ollama` (lokal, Standard) oder `openai-compatible` |
| `LC_AI_BASE_URL` | Basis-URL des Dienstes (ohne `/chat/completions`) |
| `LC_AI_MODEL` | Modellname beim Dienst |
| `LC_AI_API_KEY` / `LC_AI_API_KEY_FILE` | Schlüssel direkt oder aus einer Datei (bevorzugt; Rechte 600) |
| `LC_AI_TIMEOUT_SECONDS` | Zeitlimit je Anfrage |

`openai-compatible` spricht die verbreitete „Chat-Completions“-Schnittstelle und funktioniert damit mit gehosteten Anbietern (z. B. Mistral: `https://api.mistral.ai/v1`, OpenAI), mit vLLM, LM Studio, Ollama (`…/v1`) und KI-Gateways. Externe Dienste müssen per HTTPS angebunden werden und brauchen einen Schlüssel. Der Schlüssel steht nur im Authorization-Header und wird nie protokolliert. Ein weiterer Anbieter mit anderer Schnittstelle wird als zusätzliche Klasse hinter `LlmClient` angebunden; sonst ändert sich nichts.

## Was die KI tut – und was nicht
- **Vorschläge je Dokument** (Dialog „Dokumentangaben“ → *KI-Vorschläge holen*): Pro Dokumenttyp werden nur passende Felder abgefragt (z. B. bei der Rechnung keine Konsignatarin). Nur Felder, die weder gefüllt noch von der Regel-Erkennung vorgeschlagen sind.
- **Prüfung jeder Antwort:** Jeder Wert braucht ein **wörtliches Zitat**, das im Dokument steht und den Wert stützt (Text enthalten; Zahlen und Daten gleich nach Normalisierung, z. B. 12.345,60 = 12345.6). Alles andere wird verworfen. Das verhindert erfundene Werte, nicht jede falsche Zuordnung – deshalb immer prüfen.
- **KI-Hinweise** (Prüfung → *KI-Hinweise*): unverbindliche Anregungen zu Unterschieden zwischen den Dokumenten, jeweils mit Zitat als Beleg; Hinweise ohne belegbares Zitat werden verworfen. Sie fließen **nie** in die Regelprüfung ein, die Prüfentscheidung bleibt deterministisch.
- Nichts wird automatisch gespeichert; KI-Vorschläge sind nicht Teil von „Alle übernehmen“.
- Jede Anfrage wird im Audit vermerkt (Anbieter, Modell, Anzahl); ebenso das Ein- und Ausschalten.

## Vor dem Einsatz mit echten Daten klären
Bei externen Diensten verlassen Dokumenttexte das Haus. Vorab: Auftragsverarbeitungsvertrag, Zero Data Retention bzw. Ausschluss von Training mit API-Daten, Standort der Verarbeitung (EU), Freigabe durch Datenschutz/Compliance (Bankgeheimnis, Mandantentrennung). Lokale Modelle (Ollama, vLLM) vermeiden das, brauchen aber passende Hardware (kleine Modelle lesen verrauschte Scans deutlich schlechter).

## Schnittstellen
`GET /api/ai/status`, `PUT /api/ai/settings` (Recht `PERM_SETTINGS_MANAGE`), `POST /api/lcs/{lcId}/documents/{id}/rule-facts/ai-suggestions` und `POST /api/lcs/{lcId}/ai-hints` (Recht `PERM_DOCUMENT_UPLOAD`).
