# Domain Events und Webhooks

Webhooks (Einstellungen → **Webhooks**, Recht „Einstellungen verwalten“) schicken Ereignisse eines Mandanten per HTTPS-POST an externe Systeme.

## Ereignisse
Ereignisse entstehen aus den Audit-Aktionen; kein Modul muss etwas melden. Pro Webhook sind „alle Ereignisse“ oder eine Auswahl möglich.

| Ereignis | Audit-Aktionen |
|---|---|
| `DocumentUploaded` | `DOCUMENT_UPLOADED`, `DOCUMENT_BATCH_UPLOADED`, `DOCUMENT_ARCHIVE_UPLOADED`, `DOCUMENT_INBOX_ATTACHED`, `INBOX_RECEIVED` |
| `OcrCompleted` | `DOCUMENT_RE_RECOGNIZED`, `DOCUMENT_INBOX_EXTRACTION_RETRIED` (die Erkennung beim Hochladen gehört zu `DocumentUploaded`) |
| `ValidationCompleted` | `DOCUMENT_CHECK_REPORT_CREATED` |
| `DiscrepancyDetected` | `DOCUMENT_DISCREPANCY_CONFIRMED` |
| `ApprovalRecorded` | `DOCUMENT_DRAFT_APPROVAL_RECORDED` |
| `LcCreated` | `LC_CREATED`, `SWIFT_IMPORTED` |
| `AmendmentReceived` | `MT707_IMPORTED` |
| `AmendmentDecided` | `AMENDMENT_ACCEPTED`, `AMENDMENT_REJECTED` |
| `EbicsOrderFailed` | `EBICS_FETCH_FAILED`, `EBICS_KEYS_FAILED`, `EBICS_BANK_KEYS_FAILED` |

Noch nicht erzeugt werden `ApprovalRequired`, `StatementReceived` und `PaymentReceived` (es gibt weder Freigabe-Anforderung als Ereignis noch Kontoauszüge oder Zahlungen). Fehlgeschlagene Aktionen lösen nur `EbicsOrderFailed` aus.

## Nachricht
`POST` mit `Content-Type: application/json`:
```json
{"id":"…","type":"DocumentUploaded","occurredAt":"2026-10-22T15:32:12Z","tenantId":"…","action":"DOCUMENT_UPLOADED","entity":{"type":"LETTER_OF_CREDIT","id":"…"},"actor":"anna","details":"rechnung.pdf"}
```
Kopfzeilen: `X-LCM-Event`, `X-LCM-Delivery` (eindeutig je Zustellung), `X-LCM-Timestamp` (Sekunden seit 1970) und
`X-LCM-Signature: sha256=<hex>` = HMAC-SHA256 über `Zeitstempel + "." + Nachrichtentext` mit dem Geheimnis des Webhooks. Der Empfänger rechnet die Signatur nach und lehnt zu alte Zeitstempel ab.
Das Geheimnis (`whsec_…`) wird **nur einmal** bei Anlegen oder „Neues Geheimnis“ angezeigt.

## Zustellung
- Erfolg ist eine Antwort 2xx innerhalb von 10 Sekunden; Weiterleitungen werden nicht verfolgt.
- Bei Fehlern wird mit wachsendem Abstand wiederholt (30 s bis höchstens 1 Stunde), standardmäßig bis zu 8 Versuche (`WEBHOOK_MAX_ATTEMPTS`), danach „fehlgeschlagen“; „Erneut senden“ startet eine Zustellung neu. Gleiche Ereignisse können mehrfach ankommen (Zustellungs-Kennung zum Entdoppeln nutzen).
- Zugestellte Einträge werden nach 14 Tagen, fehlgeschlagene nach 60 Tagen gelöscht. „Testen“ sendet ein `Ping`-Ereignis.

## Sicherheit
- Nur **https://**; Ziele im internen Netz (Loopback, private, Link-Local- und Carrier-NAT-Adressen) sind verboten und werden beim Speichern und vor jeder Zustellung geprüft (Schutz vor Server-Side Request Forgery).
- Das Geheimnis liegt mit AES-256-GCM verschlüsselt in der Datenbank, Schlüssel `WEBHOOK_ENCRYPTION_KEY` (mindestens 32 Zeichen; fällt auf `EBICS_ENCRYPTION_KEY` zurück). Ohne Schlüssel lassen sich keine Webhooks anlegen.
- Nur für Entwicklung und Tests: `WEBHOOK_ALLOW_PRIVATE_TARGETS=true` erlaubt interne Ziele und `http://`.
- Anlegen, Ändern, neues Geheimnis, Test, Löschen und Neustart stehen im Audit-Protokoll. Je Mandant getrennt (Row-Level-Security, Migration V139).
- Die Zustellung ist eine **Outbox je Webhook** in derselben Transaktion wie der Audit-Eintrag; ein Ausfall des Empfängers bremst die Anwendung nicht.
