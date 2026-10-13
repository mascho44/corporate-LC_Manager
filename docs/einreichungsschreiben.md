# Einreichungsschreiben und Kundenstamm

Eingehende Dokumente werden einem **Kunden** über das Einreichungsschreiben zugeordnet. Dort stehen Kunde, Kundennummer, Kontonummer oder IBAN.

## Kundenstamm
Mandanteneinstellungen → *Kundenstamm* (Recht „Einstellungen verwalten"; `GET/POST/PUT/DELETE /api/customers`). Je Kunde: Name, Kundennummer (je Mandant eindeutig), Kontonummer,
IBAN (Prüfziffer wird geprüft, gespeichert ohne Leerzeichen), BIC. Die Spalten `source` und `external_ref` sind für **Konnektoren zu weiteren Quellen** vorgesehen (z. B. Kernsystem, ERP);
heute gibt es nur die Quelle `MANUAL`.

## Erkennung im Posteingang
* Ein Dokument gilt als Einreichungsschreiben, wenn es als „Dokumenteneinreichung" erkannt wird oder mindestens drei Kundenangaben nennt.
* Gelesen werden beschriftete Angaben (deutsch und englisch): *Kunde / Customer*, *Kundennummer / Customer no.*, *Kontonummer / Account number*, *IBAN*, *BIC*.
  Eine IBAN zählt nur mit richtiger Prüfziffer.
* Abgleich mit dem Kundenstamm in dieser Rangfolge: IBAN, Kundennummer, Kontonummer (ohne Format und führende Nullen), Name. Nur ein **eindeutiger Treffer über IBAN, Kundennummer oder
  Kontonummer** gilt als klarer Vorschlag; Namen allein, mehrere Treffer oder kein Treffer verlangen die Auswahl durch eine Person.
* Im Posteingang erscheint am Dokument der Block *Einreichungsschreiben · Kunde*: gelesene Angaben, der Vorschlag mit **Bestätigen**, eine Auswahlliste aller Kunden und *Zuordnung entfernen*.
  Gespeichert wird die bestätigte oder gewählte Zuordnung (`inbox_customer_assignment`, Methode `SUGGESTION` oder `MANUAL`, im Audit-Protokoll).
  Es wird nichts ohne Bestätigung zugeordnet.

## Offen
Die Zuordnung zum Kunden steuert noch nicht die Auswahl der LC-Akte; Konnektoren zu weiteren Quellen; Feintuning der Beschriftungen an echten Einreichungsschreiben.
