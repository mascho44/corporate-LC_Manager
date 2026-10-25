# Korrespondenzbanken

Stammdaten der Banken, mit denen ein Mandant Akkreditive und Nachrichten austauscht. Seite **Korrespondenzbanken** im Menü (Recht „Einstellungen verwalten“; Lesen für alle Angemeldeten über die Schnittstelle).

| Feld | Bedeutung |
|---|---|
| BIC | 8 oder 11 Zeichen (z. B. `DEUTDEFF` oder `DEUTDEFF500`), je Mandant eindeutig |
| Name, Ort, Land | Land als ISO-Code mit zwei Buchstaben |
| Kommunikationswege | SWIFT, EBICS, E-Mail, Bankportal (Mehrfachauswahl) |
| Bevorzugter Weg | muss unter den Kommunikationswegen sein |
| Weiterleitung über BIC | optional, wenn Nachrichten an diese Bank über eine andere BIC laufen |
| E-Mail, Telefon, Hinweise | Ansprechpartner und Besonderheiten |
| Aktiv | inaktive Banken bleiben gespeichert |

- Anlegen, Ändern und Löschen stehen im Audit-Protokoll (`CORRESPONDENT_BANK_CREATED`, `_UPDATED`, `_DELETED`).
- Schnittstelle: `GET/POST /api/correspondent-banks`, `PUT/DELETE /api/correspondent-banks/{id}`, `GET /api/correspondent-banks/channels`. Schreibend nur mit dem Recht „Einstellungen verwalten“.
- Je Mandant getrennt (Row-Level-Security), wird beim Löschen des Mandanten mit entfernt (Migration V137).
- Noch nicht: Verknüpfung mit Akten (Avisierende/Bestätigende Bank aus `:52A:`/`:57A:`), Nutzung für den Versandweg und für Compliance-Prüfung (Issue 62) und Exposure-Limits (Issue 60).
