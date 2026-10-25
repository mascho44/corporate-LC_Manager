# Zahlungsstatus der Akte

In der Akte zeigt der Block **Zahlung**, ob das Geld da ist: **noch keine Zahlung**, **teilweise bezahlt**, **bezahlt** oder **mehr als der Akkreditivbetrag**, dazu eingegangen und offener Betrag und die erfassten Zahlungen.
LCM liest **keine Kontoauszüge**: Die Zahlungen kommen aus zwei Wegen, weil die Konten in der Praxis in einem anderen System und einer anderen Abteilung liegen.

## Erfassen
- **Von Hand:** *Zahlung erfassen* in der Akte (Recht „LC-Akten bearbeiten“): Eingangsdatum (nicht in der Zukunft), Betrag, Währung (Vorgabe: die des Akkreditivs), Referenz und Hinweis. Der Betrag ist mit dem offenen Rest vorbelegt. Teilzahlungen sind möglich, höchstens 200 je Akte; *Löschen* nimmt eine Zahlung zurück.
- **Per API:** Ein anderes System meldet den Eingang mit einem API-Schlüssel mit dem Umfang `PAYMENT`: `POST /api/v1/lcs/{id}/payments` mit `{"receivedOn":"2026-10-21","amount":75000,"currency":"EUR","reference":"ERP-4711"}`. Lesen mit `GET /api/v1/lcs/{id}/payments` (Umfang `READ`); `GET /api/v1/lcs/{id}` enthält die Zusammenfassung als `payment`. Siehe [REST-API](rest-api.md).

Beides steht im Audit-Protokoll (`LC_PAYMENT_RECORDED`, `LC_PAYMENT_DELETED`, mit Quelle „MANUAL“ oder „API“) und löst bei Webhooks das Ereignis `PaymentReceived` aus ([Webhooks](webhooks.md)).

## Status
- Es zählen nur Zahlungen in der **Währung des Akkreditivs**; Zahlungen in anderer Währung werden gespeichert und angezeigt, zählen aber nicht („in anderer Währung erfasst“).
- **Erwartet bis:** der früheste Termin „Voraussichtliche Fälligkeit“ aus den [Fristen](erwarteter-geldeingang.md). Ist er vorbei und noch nicht alles bezahlt, steht „überfällig“ (rotes Kennzeichen).
- Ob eine Zahlung zur Akte gehört, beurteilt die Person oder das meldende System; LCM gleicht nichts ab. Abweichende Beträge werden angezeigt, nicht bewertet.
