# Benutzer & Rechte (Mandanten-Administration)

Der Menüpunkt **Benutzer** öffnet für Mandanten-Admins (Recht „Benutzer verwalten") die Seite **Benutzer & Rechte** mit drei Reitern. Der frühere lange Dialog „Benutzerverwaltung" ist darüber nicht mehr erreichbar; die Stammdaten des Mandanten (Kennung, Profile) stehen als Kurzzeile unter der Überschrift.

* **Mitglieder:** Tabelle mit Benutzer, Rolle, Status und Aktion; Suche (Name, Rolle, Status) sowie Filter nach Rolle und Status. Die Rolle wird direkt in der Zeile gewählt und sofort gespeichert („✓ gespeichert"; bei einem Fehler springt die Auswahl zurück). Die eigene Rolle ist gesperrt und der eigene Zugang kann nicht gesperrt werden (Schutz vor Aussperren). „Sperren/Aktivieren" betrifft nur den Zugang zu diesem Mandanten.
* **Einladungen:** Neue Personen per E-Mail einladen, offene Einladungen (erneut senden, widerrufen) und – in gemeinsamen Mandanten – ein vorhandenes Konto zuordnen.
* **Rollen & Rechte:** Matrix aller Rechte (nach Bereich gruppiert) je Rolle mit Häkchen und der Zahl der Mitglieder je Rolle; „Rollen bearbeiten" öffnet den Rolleneditor.

Die Schnittstellen sind unverändert (`/api/users/memberships…`, Einladungen, Rollen); die Seite ersetzt nur die Oberfläche.
