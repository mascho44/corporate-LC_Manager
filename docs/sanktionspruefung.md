# Sanktionsprüfung

Die Parteien jeder LC-Akte werden gegen die öffentlichen Sanktionslisten geprüft. Die Prüfung unterstützt die Sachbearbeitung; sie ersetzt kein zertifiziertes Screening-System.

## Listen
- **EU**: Konsolidierte Finanzsanktionsliste (XML)
- **UN**: Konsolidierte Liste des Sicherheitsrats (XML)
- **OFAC**: SDN-Liste mit Aliasdatei (CSV)

Abruf etwa zehn Minuten nach dem Start, falls noch keine Liste gespeichert ist, danach täglich um 03:30 Uhr; manuell über *Plattformadministration → Übersicht → Sanktionslisten → Jetzt aktualisieren*.
Jeder neue Stand wird mit Abrufzeit und SHA-256 gespeichert (Tabelle `sanctions_list`, gzip, die letzten 30 Stände je Quelle). Ein Befund nennt den Stand („Liste vom …“). Schlägt ein Abruf fehl, wird mit dem letzten Stand weitergeprüft und der Fehler in der Übersicht angezeigt.

## Geprüfte Parteien
Auftraggeber, Begünstigter, eröffnende, avisierende und bestätigende Bank, Remboursbank, bezogene Bank, „benutzbar bei“. Geprüft wird die Namenszeile (Kontozeilen „/…“ und BICs werden übersprungen).

## Abgleich
Namen werden normalisiert (Akzente, Groß-/Kleinschreibung, Satzzeichen, Rechtsformen wie GmbH/Ltd/AG entfallen) und wortweise unscharf verglichen (Jaro-Winkler, Wortreihenfolge egal). Einwortnamen zählen nur ab sechs Zeichen.
- ab 0,92: **Sanktionslisten-Treffer** (`SANCTIONS_MATCH`, Abweichung, blockierend)
- 0,80 bis 0,92: **möglicher Treffer** (`SANCTIONS_POSSIBLE_MATCH`, Hinweis, blockierend)
- ohne Treffer: Vermerk `SANCTIONS_SCREENED`; ohne geladene Listen `SANCTIONS_NOT_SCREENED`

Die Befunde erscheinen im Prüfmodus unter „Blockierend“ und werden wie andere Befunde entschieden.

## Schiffe, Dokument-Parteien, Embargoländer (Schritt 2)
- **Schiffe** (erkanntes Feld „Genanntes Schiff“ im Transportdokument): zuerst über eine IMO-Nummer im Text (`SANCTIONS_VESSEL_MATCH`, OFAC führt rund 1.500 Schiffe mit IMO), sonst über den Namen gegen gelistete Schiffe (`SANCTIONS_VESSEL_POSSIBLE_MATCH` ab 0,95). Reisenummern („V.0123“) und Vorsätze („MV“) werden vor dem Vergleich entfernt.
- **Parteien aus den Dokumenten**: Empfänger (Consignee), Notify Party, Frachtführer; dazu Empfänger und Notify Party der Akte.
- **Embargoländer** (`EMBARGO_COUNTRY`, Hinweis, blockierend): Iran, Nordkorea, Syrien, Kuba, Russland, Belarus, Krim und besetzte Gebiete der Ukraine, jeweils mit üblichen Schreibweisen und wichtigen Häfen. Geprüft werden Ursprungsland, Lade-/Löschhafen, Flughäfen und Adressländer aus Akte und Dokumenten sowie die Parteienfelder der Akte. Ein reiner ISO-Code („RU“) zählt nur, wenn das Feld nur aus dem Code besteht. Erweiterbar über `app.sanctions.embargo-extra`, z. B. `Venezuela:VENEZUELA|CARACAS`. Die Länderprüfung braucht keine geladenen Listen.

## Vier-Augen-Freigabe und Falsch-Positiv-Gedächtnis (Schritt 3)
- **Treffer bestätigen** (`CONFIRMED_DISCREPANCY`): wie bisher in einem Schritt.
- **Als Falsch-positiv vormerken**: die erste Person (Recht „Dokumente prüfen“) begründet; der Treffer bleibt offen und zeigt „Als Falsch-positiv vorgemerkt … Freigabe ausstehend“.
- **Falsch-positiv freigeben (Vier-Augen)**: eine *andere* Person mit dem neuen Recht **„Sanktionstreffer freigeben“** (`SANCTIONS_REVIEW`, Administratoren erhalten es automatisch) gibt frei. Dieselbe Person oder jemand ohne das Recht wird abgewiesen.
- Nach der Freigabe merkt sich der Mandant die Kombination aus Partei und Listeneintrag (Tabelle `sanctions_clearance`). In späteren Prüfungen – auch in anderen Akten – erscheint statt des Treffers der Vermerk `SANCTIONS_CLEARED` mit den beiden Namen und dem Datum. Ändert sich der Listeneintrag (Namen, Aliase, Programm, IMO), wird wieder gemeldet.
- **Neue Listenstände** wirken sofort: jede Prüfung läuft gegen den aktuellen Stand. Das Cockpit zeigt die Kachel **„Sanktionstreffer offen“**, sobald eine Akte offene Treffer hat.
- Gelöschte Mandanten: das Gedächtnis wird mit gelöscht.

## Benachrichtigung bei veralteten Listen
Ist eine Liste noch nicht geladen, älter als 48 Stunden oder ist der letzte Abruf fehlgeschlagen, bekommen die aktiven Plattformadministratoren ab 7 Uhr **höchstens einmal täglich** eine E-Mail mit den betroffenen Listen. Zusätzlich steht der Hinweis in der Plattformadministration (Übersicht). Voraussetzung: E-Mail-Versand eingerichtet.
