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

## Geplant
Teil 2: Abruf von MT700/707/710/760 per BTD (Service `TRC`) in den SWIFT-Import; Teil 3: zeitgesteuerter Abruf.
