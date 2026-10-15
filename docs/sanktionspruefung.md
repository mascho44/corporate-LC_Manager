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

## Noch nicht enthalten (folgende Schritte)
Neuprüfung offener Akten bei neuem Listenstand mit Hinweis; Gedächtnis für bestätigte Falsch-Positive; eigenes Recht für die Vier-Augen-Freigabe von Treffern.
