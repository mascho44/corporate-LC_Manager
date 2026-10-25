# SWIFT-Ordner: Nachrichten vom Nachrichtensystem der Bank empfangen

Für Banken mit eigenem SWIFT-Zugang: Das Nachrichtensystem der Bank (Gateway, Nachrichtenverteiler) legt Kopien der eingegangenen Nachrichten in einem Ordner ab, LCM liest sie. **Nur Empfang**: LCM schreibt nie etwas zurück und sendet nichts. Senden und Bearbeiten von Akkreditiven bleibt in der führenden Software; LCM ergänzt.

## Einschalten und Ordner
- Auf dem Server `SWIFT_FOLDER_ENABLED=true` setzen (Standard: aus). Nur **Mandanten mit Bank-Profil** werden gelesen.
- Je Mandant gibt es unter `SWIFT_FOLDER_ROOT` (Standard `/data/swift`): `<Mandanten-ID>/eingang` (hier legt das Nachrichtensystem ab), `verarbeitet` und `fehler`. Die Ordner entstehen beim ersten Lauf; der genaue Pfad steht auf der Seite *EBICS-Bankanbindung* im Bereich „Abgeholte Nachrichten“.
- Das Verzeichnis des Containers muss von außen erreichbar sein (Volume oder Bind-Mount, zum Beispiel eine Freigabe, in die das Nachrichtensystem schreibt). Ein Lesezugriff für LCM genügt nicht ganz: LCM verschiebt gelesene Dateien in `verarbeitet` und `fehler`, braucht also Schreibrecht **in diesem einen Ordnerbaum**, aber nirgends im SWIFT-System.
- Alle 30 Sekunden (`SWIFT_FOLDER_POLL_MS`) werden bis zu 50 fertige Dateien gelesen; eine Datei muss 10 Sekunden unverändert sein (`SWIFT_FOLDER_SETTLE_SECONDS`), versteckte und unfertige Dateien (`.x`, `~`, `.tmp`, `.part`) bleiben liegen, Symlinks werden ignoriert.

## Formate
Eine Datei darf eine oder mehrere Nachrichten enthalten. Erkannt werden:
- reiner SWIFT-Text mit den Feldern (`:20:` …),
- FIN mit Blöcken (`{1:…}{2:…}{4:…-}{5:…}`), auch mehrere hintereinander,
- RJE: Nachrichten mit `$` in einer eigenen Zeile getrennt, mit Steuerzeichen (SOH, ETX) und Windows-Zeilenenden.
Text muss UTF-8 sein, höchstens 512 KB je Datei. Das genaue Format eures Systems ist noch zu klären; weicht es ab, ist das eine kleine Anpassung des Lesers (`SwiftFileSplitter`).

## Was passiert
- Der Typ wird erkannt (MT700, MT701, MT707, MT710, MT720, MT734, MT750, MT760, MT199, MT799); jede Nachricht wird **einmal je Inhalt** abgelegt, dieselbe Nachricht aus einer zweiten Datei zählt als „bereits bekannt“.
- Sie erscheint wie eine abgeholte EBICS-Nachricht unter *EBICS-Bankanbindung → Abgeholte Nachrichten* mit dem Zusatz „Ordner: <Datei>“, und zwar mit **Ansehen, Importieren, Verwerfen**. **Nichts wird automatisch importiert.** Die Gruppeninbox-Aufgabe „neue Bankmitteilung“ entsteht wie beim EBICS-Abruf.
- Gelesene Dateien wandern mit Zeitstempel nach `verarbeitet`; abgelehnte (leer, zu groß, kein Text, keine SWIFT-Felder, unbekannter Typ) nach `fehler` mit einer Datei `….fehler.txt` und dem Grund. Nichts wird gelöscht.
- Audit-Protokoll: `SWIFT_FOLDER_RECEIVED` und `SWIFT_FOLDER_REFUSED` (Benutzer `system:swift-folder`).

## Noch offen
- Eine eigene Seite „Nachrichteneingang“ (heute unter der EBICS-Seite; sichtbar mit dem Recht „Einstellungen verwalten“ und für den Import mit „SWIFT-Import“).
- Direktanbindung an MQ oder Web-Dienste des Nachrichtensystems (hängt von deiner Recherche ab).
