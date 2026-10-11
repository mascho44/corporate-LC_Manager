# KI-Vorschläge (lokales Modell, optional)

Standardmäßig **aus**. Aktivierung über Umgebungsvariablen: `LC_AI_ENABLED=true`, `LC_AI_BASE_URL` (Ollama, Standard `http://localhost:11434`), `LC_AI_MODEL` (Standard `qwen2.5:3b`), `LC_AI_TIMEOUT_SECONDS`.

- Der Dokumenttext geht ausschließlich an die konfigurierte `base-url`.
- Im Dialog „Dokumentangaben“ erscheint die Schaltfläche **KI-Vorschläge holen**. Die Anfrage dauert je nach Rechner bis zu einer Minute und läuft nie automatisch.
- Jeder Vorschlag braucht ein **wörtliches Zitat**, das im Dokument steht und den Wert enthält; andere Antworten werden verworfen. Das verhindert erfundene Werte, **nicht** falsch zugeordnete (z. B. Vertragsnummer statt Rechnungsnummer). Deshalb: immer prüfen, nichts wird automatisch gespeichert, und KI-Vorschläge sind **nicht** Teil von „Alle Vorschläge übernehmen“.
- Gefragt werden nur Textfelder, die weder gefüllt noch von der Regel-Erkennung vorgeschlagen sind. Jede Anfrage wird im Audit protokolliert (Modell, Anzahl).
- Endpunkt: `POST /api/lcs/{lcId}/documents/{id}/rule-facts/ai-suggestions` (Recht `PERM_DOCUMENT_UPLOAD`); Status: `GET /api/ai/status`.
