# EBICS-Anbindung (Teil 1: Verbindung und Schlüssel)

Pro Mandant gibt es genau eine EBICS-Verbindung zu einer Bank (oder zum Testsimulator `evils-ebics-server`).
LCM tritt mit einem **eigenen Teilnehmer** auf (EBICS 3.0 / H005, A006, X002, E002); es werden keine Schlüssel
anderer Systeme übernommen.

Einrichtung unter *EBICS-Bankanbindung* (Recht `SETTINGS_MANAGE`):

1. Bank-URL (https), Host-ID, Partner-ID und Teilnehmer-ID speichern.
2. *Schlüssel erzeugen und senden*: erzeugt die Schlüssel, speichert sie verschlüsselt und sendet INI und HIA.
   Die Fingerabdrücke (SHA-256) dienen für den INI-Brief.
3. Die Bank gibt den Teilnehmer frei (im Simulator: Admin-Konsole, Teilnehmer freigeben).
4. *Bankschlüssel abholen* (HPB): danach ist die Verbindung aktiv.
5. *Zurücksetzen* verwirft die Schlüssel (z. B. wenn die Bank den Teilnehmer zurückgesetzt hat).

## Sicherheit
- Die Schlüssel liegen nur AES-256-GCM-verschlüsselt in `ebics_connection`; der Schlüssel dafür ist
  `EBICS_ENCRYPTION_KEY` (mindestens 32 Zeichen, nicht im Repository). Die Zeilen-ID ist Teil der Authentisierung.
  Ohne diesen Wert werden keine Schlüssel erzeugt.
- Beim Entschlüsseln wird die Java-Deserialisierung per Allowlist auf EBICS-/Krypto-Klassen beschränkt.
- Die URL muss `https` sein, darf keine Zugangsdaten enthalten und nicht auf lokale/private Adressen zeigen.
  Interne Hosts müssen in `EBICS_ALLOWED_HOSTS` (kommagetrennt) freigegeben werden.
- Alle Aktionen (Anlegen, Schlüssel senden, HPB, Zurücksetzen) stehen im Audit-Protokoll, ohne Schlüsselmaterial.

## Teil 2: Nachrichten abholen
Bei aktiver Verbindung holt *Nachrichten abholen* MT700, MT707, MT710 und MT760 per BTD (Service `TRC`, MsgName = Nachrichtentyp).
- Jede Nachricht wird einmal je Mandant abgelegt (SHA-256 des Inhalts); erneutes Abholen derselben Nachricht zählt als „bereits bekannt“.
- **Nichts wird automatisch angelegt.** Pro Nachricht: *Ansehen* (Vorschau des SWIFT-Imports mit Fehlern und Hinweisen),
  *Importieren* (wie der manuelle SWIFT-Import; MT707 setzt Prüfentscheidungen zurück) oder *Verwerfen*.
- Alle Typen sind importierbar: MT700/MT710 und MT760 als Akte (MT760 als Garantie), MT707 als Änderung, MT199/MT799 als Bankmitteilung.
- Welche Typen abgeholt werden, steuert `EBICS_MESSAGE_TYPES` (Standard `MT700,MT707,MT710,MT760`; zusätzlich `MT199`, `MT799`). Der Testsimulator liefert MT199/MT799 derzeit nicht; die Typen erst eintragen, wenn die Bank sie bereitstellt.
- Recht: `SWIFT_IMPORT` für Liste, Abruf, Vorschau, Import und Verwerfen. Alle Aktionen stehen im Audit-Protokoll.
- Antworten der Bank werden als Text (UTF-8, höchstens 512 KB) geprüft; Fehler eines Nachrichtentyps stoppen die anderen nicht.

## Teil 3: automatischer Abruf
Unter *Abgeholte Nachrichten* lässt sich *Automatisch abrufen* einschalten (Intervall 5 Minuten bis 24 Stunden, Standard aus).
- Nur bei aktiver Verbindung. Ein Hintergrundjob prüft jede Minute je Mandant, ob der Abruf fällig ist, und holt dann genau wie der Button ab.
- Der Abruf **legt nur ab**. Importiert wird weiterhin ausschließlich nach Bestätigung durch einen Nutzer.
- Der Menüpunkt zeigt die Zahl neuer, noch nicht bearbeiteter Nachrichten (ohne MT760), z. B. „EBICS-Bankanbindung (2)“.
- Letzter Abruf und Ergebnis stehen im Panel; Audit-Einträge laufen unter dem Benutzer `system:ebics-auto-fetch`.
- Fehler stoppen den Job nicht; der nächste Versuch folgt nach dem eingestellten Intervall.
