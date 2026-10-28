# Einrichtungsassistent

Mandantenadministratoren (Rechte „Benutzer verwalten“ und „Einstellungen verwalten“) finden im Cockpit unter **Erste Schritte** den Knopf **Einrichtungsassistent starten**. Der Assistent fragt die Stammdaten Schritt für Schritt ab und speichert sie in den Firmenstammdaten (`/api/companies`, die erste Firma des Mandanten; gibt es keine, wird sie angelegt). Jeder Schritt wird beim Klick auf „Weiter“ gespeichert, „Zurück“ ändert nichts.

Die Schritte hängen vom Profil des Mandanten ab:

| Profil | Schritte |
|---|---|
| **Corporate** | Firma (Name, Anschrift) → Kontakt und Register (Ansprechpartner, E-Mail, Telefon, Handelsregister, Steuernummer/USt-ID) → Bankverbindung (Bank, IBAN, BIC) → Logo → Weitere Einrichtung (Benutzer einladen, Dokumentvorlagen) |
| **Bank** | Bank (Name, Anschrift, BIC) → Kontakt und Register → Logo → Weitere Einrichtung (Benutzer einladen, SLA-Regel, Korrespondenzbank) |
| **Beides** | wie Corporate, mit BIC im ersten Schritt, und die Folgeschritte beider Profile |

Das Logo ist optional. Der letzte Schritt enthält Knöpfe zu den Seiten, die der Assistent nicht selbst ausfüllt. Knöpfe für Seiten ohne Berechtigung sind deaktiviert. Die Checkliste „Erste Schritte“ aktualisiert sich nach „Fertig“.

Texte: Deutsch und Englisch (`sw.*` in den Sprachpaketen). Umsetzung: `setup-wizard.js`; die Schrittfolge (`plan`) ist in `setup-wizard.test.cjs` getestet.
