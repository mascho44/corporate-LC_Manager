# Benutzer & Rechte (Mandanten-Administration)

Der Menüpunkt **Benutzer** öffnet für Mandanten-Admins (Recht „Benutzer verwalten") die Seite **Benutzer & Rechte** mit drei Reitern. Der frühere lange Dialog „Benutzerverwaltung" (mit dem seit Langem ausgeblendeten Formular zum Anlegen von Konten) ist entfernt; die Stammdaten des Mandanten (Kennung, Profile) stehen als Kurzzeile unter der Überschrift.

* **Mitglieder:** Tabelle mit Benutzer, Rolle, Status und Aktion; Suche (Name, Rolle, Status) sowie Filter nach Rolle und Status. Die Rolle wird direkt in der Zeile gewählt und sofort gespeichert („✓ gespeichert"; bei einem Fehler springt die Auswahl zurück). Die eigene Rolle ist gesperrt und der eigene Zugang kann nicht gesperrt werden (Schutz vor Aussperren). „Sperren/Aktivieren" betrifft nur den Zugang zu diesem Mandanten.
* **Einladungen:** Neue Personen per E-Mail einladen, offene Einladungen (erneut senden, widerrufen) und – in gemeinsamen Mandanten – ein vorhandenes Konto zuordnen.
* **Rollen & Rechte:** Matrix aller Rechte (nach Bereich gruppiert) je Rolle mit Häkchen und der Zahl der Mitglieder je Rolle; „Rollen bearbeiten" öffnet den Rolleneditor.

Die Schnittstellen sind unverändert (`/api/users/memberships…`, Einladungen, Rollen); die Seite ersetzt nur die Oberfläche.

## Mehrfachaktionen und mehrere Einladungen
* **Auswahl in der Mitgliederliste:** Mit den Häkchen (links, oben für alle angezeigten) erscheint eine Leiste mit „Rolle setzen", „Sperren" und „Aktivieren" für alle Ausgewählten. Das System fragt vorher nach, nennt die Zahl der betroffenen Mitglieder und überspringt (mit Grund): das eigene Konto, Mitglieder die die Rolle schon haben bzw. schon gesperrt/nicht gesperrt sind und Mitglieder mit inaktivem Konto. Danach steht die Bilanz („3 von 3 geändert, 1 übersprungen (…)") über den Reitern.
* **Mehrere Personen einladen:** Unter „Einladungen" → „Mehrere Personen auf einmal einladen": eine Person pro Zeile, als „Name <E-Mail>", „E-Mail" oder „E-Mail; Name" (ohne Name wird er aus der Adresse abgeleitet), höchstens 50, doppelte Adressen und fehlerhafte Zeilen werden gemeldet. Alle bekommen die oben gewählte Rolle; das Ergebnis steht je Zeile darunter.
* **Rechte in Kurzform:** Beim Einladen und Zuordnen steht statt der langen Liste z. B. „Alle 13 Rechte" oder „11 von 13 Rechten – es fehlen: …" mit dem Link „Rollen vergleichen" (öffnet die Matrix).

## Entfernt
Dialog „Benutzerverwaltung" samt Kopfzeilen-Knopf, Benutzerliste, Kontoformular und die alte Mitgliedschaftsübersicht sind aus der Oberfläche entfernt. Der Rolleneditor („Rollen bearbeiten" im Reiter Rollen & Rechte) und „Vorhandenen Benutzer zuordnen" bleiben. Die Schnittstellen `/api/users` (Konten anlegen, ändern, löschen) bestehen im Server weiter, werden von der Oberfläche aber nicht mehr aufgerufen.

Auch im Server gibt es keine Schnittstellen mehr zum Anlegen, Ändern oder Löschen von Konten unter `/api/users` (`GET/POST /api/users`, `PUT/DELETE /api/users/{id}`): Konten entstehen über Einladungen und die Plattformverwaltung, Mandanten-Admins verwalten Rolle und Zugang unter `/api/users/memberships`. Erhalten bleibt `GET /api/users/assignable` (Auswahl für Aufgaben). Die zugehörigen Methoden in `UserService` sind noch vorhanden und getestet, werden aber nicht mehr aufgerufen.

## Seite „Mandant" (Mandantenverwaltung)
Der Menüpunkt **Mandant** (früher „Tenants") ist in Reiter gegliedert:
* **Übersicht:** Zugang zum Mandanten (Name, Kennung, Profil Bank/Corporate), **Stammdaten des Mandanten** (Name, Standardsprache) und Einrichtungsstand mit Sprunglinks (Firmenstammdaten, Dokumentvorlagen, Mitglieder).
* **Erkennung & KI:** Scan-Profil, KI-Vorschläge, gelernte Dokumentarten.
* **Daten & Zugänge:** Kundenstamm, Erfassungszugänge (Poststelle).

Die Mandantenliste und der Umschalter im Kopf erscheinen nur, wenn es mehrere Mandanten gibt oder neue angelegt werden dürfen. Firmenstammdaten und Dokumentvorlagen bleiben eigene Menüpunkte.

## Recht „Sanktionstreffer freigeben"
`SANCTIONS_REVIEW` erlaubt die zweite Freigabe eines als Falsch-positiv vorgemerkten Sanktionstreffers (Vier-Augen-Prinzip, siehe [Sanktionsprüfung](sanktionspruefung.md)). Administratoren erhalten es automatisch; anderen Rollen wird es im Rolleneditor gegeben.
