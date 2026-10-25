# Externe REST-API (Version 1)

Systeme anderer Hersteller (ERP, Handelsplattform, Archiv) greifen mit einem **API-Schlüssel** auf einen Mandanten zu: Akten und Befunde lesen, eine Akte aus einer MT700 anlegen, Dokumente hochladen.
Den Schlüssel legt ein **Administrator** unter **API-Schlüssel** an; die Verwaltung (Seite und Schnittstelle `/api/api-keys`) steht nur Administratoren offen. Das Geheimnis (`lca_…`) wird **einmal** angezeigt; gespeichert wird nur sein SHA-256-Hash. Höchstens 20 aktive Schlüssel je Mandant, Widerruf jederzeit.

## Berechtigungen
Jeder Schlüssel trägt nur die gewählten Umfänge:

| Umfang | erlaubt |
|---|---|
| `READ` | alle `GET`-Aufrufe |
| `LC_CREATE` | `POST /api/v1/lcs` |
| `DOCUMENT_UPLOAD` | `POST /api/v1/lcs/{id}/payments` | meldet einen Zahlungseingang (`201`): JSON `receivedOn`, `amount`, `currency` (Vorgabe: Währung des Akkreditivs), `reference`, `note`; siehe [Zahlungsstatus](zahlungsstatus.md) |
| `POST /api/v1/lcs/{id}/documents` |
| `PAYMENT` | `POST /api/v1/lcs/{id}/payments` |

Ein Schlüssel gilt **nur unter `/api/v1/`** und für den Mandanten, in dem er angelegt wurde. Er hat keine Benutzerrechte, keine Sitzung und braucht keinen CSRF-Token. Fehlt der Umfang, antwortet die API mit `403` und nennt ihn;
ein ungültiger oder widerrufener Schlüssel ergibt `401`; ein Schlüssel eines stillgelegten Mandanten wird nicht akzeptiert. Angelegt, widerrufen und die Aufrufe selbst stehen im Audit-Protokoll (Benutzer `api:<Name>`).

## Aufrufe
Alle mit `Authorization: Bearer lca_…`. Fehler kommen als `{"error":"…"}`; mit der Kopfzeile `X-LC-Language: en` auf Englisch.

| Aufruf | Antwort |
|---|---|
| `GET /api/v1/lcs[?reference=…]` | Liste: `id`, `reference`, `status`, `instrumentType`, `applicant`, `beneficiary`, `currency`, `amount`, `issueDate`, `expiryDate`, `latestShipmentDate` |
| `GET /api/v1/lcs/{id}` | `lc` (wie oben), `requiredDocuments`, `documents`, `review` (`status` GREEN/YELLOW/RED, `discrepancies`, `warnings`, `passed`) |
| `GET /api/v1/lcs/{id}/findings` | `review` und `findings`: `severity`, `automaticSeverity`, `code`, `message`, `document`, `lcCondition`, `evidence`, `decision` |
| `GET /api/v1/lcs/{id}/documents` | Dokumentliste der Akte |
| `GET /api/v1/lcs/{id}/payments` | `summary` (Status, eingegangen, offen, erwartet, überfällig) und `payments`; die Zusammenfassung steht auch als `payment` in `GET /api/v1/lcs/{id}` |
| `POST /api/v1/lcs` | legt die Akte aus einer MT700 an (`201`); Text: `Content-Type: text/plain` mit der Nachricht, oder JSON `{"rawMessage":"…"}` |
| `POST /api/v1/lcs/{id}/documents` | `multipart/form-data`: `file`, `type` (z. B. `COMMERCIAL_INVOICE`), optional `documentDate` (`2026-09-18`), `amount`, `currency`, `copyNumber`; `201` mit dem Dokument |

`finding.automaticSeverity` ist der automatische Hinweis; `decision` die fachliche Entscheidung einer Person (leer, solange keine getroffen wurde). Ein grünes Ergebnis ist nie eine Freigabe.

```
curl -H "Authorization: Bearer lca_…" https://<server>/api/v1/lcs
curl -X POST -H "Authorization: Bearer lca_…" -H "Content-Type: text/plain" --data-binary @mt700.txt https://<server>/api/v1/lcs
curl -X POST -H "Authorization: Bearer lca_…" -F file=@rechnung.pdf -F type=COMMERCIAL_INVOICE https://<server>/api/v1/lcs/<id>/documents
```

## Ereignisse statt Abfragen
Für Benachrichtigungen bei Änderungen (Dokument geprüft, Abweichung bestätigt …) gibt es die [Webhooks](webhooks.md); die API ergänzt sie um die Abfrage.

## Grenzen (Stand 0.7.0)
- Keine Begrenzung der Aufrufrate je Schlüssel; Begrenzung am vorgeschalteten Reverse-Proxy einrichten.
- Version 1 kennt keine Änderungen (MT707), keine Entscheidungen über Befunde und kein Löschen; Seitenweise Abfrage fehlt (die Liste enthält alle Akten des Mandanten).
- Dokumente werden einzeln hochgeladen; eine Prüfung läuft wie bei einem Upload in der Oberfläche (Befunde neu berechnen).
