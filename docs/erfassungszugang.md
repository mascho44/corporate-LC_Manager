# Erfassungszugang für Scan-Stationen

Eine Scan-Station (z. B. in der Poststelle) liefert Dokumente über einen **Erfassungszugang** in den Posteingang eines Mandanten, ohne Benutzerkonto und Sitzung.

* **Anlegen:** Mandanteneinstellungen → *Erfassungszugänge* (Recht „Einstellungen verwalten"). Der Zugang bekommt einen Namen (z. B. „Poststelle Scanner 2"); das
  Geheimnis (`lcc_…`) wird **einmal** angezeigt. Gespeichert wird nur sein SHA-256-Hash. Höchstens 20 aktive Zugänge je Mandant, Widerruf jederzeit.
* **Was er darf:** ausschließlich `POST /api/inbox` (Datei-Upload in den Posteingang des eigenen Mandanten). Jeder andere Pfad antwortet 403. Der Zugang
  hat kein Recht außer „Dokumente hochladen", benötigt keinen CSRF-Token und erzeugt keine Sitzung. Er funktioniert nur, solange der Mandant aktiv ist.
* **Aufruf:**
  ```
  curl -X POST https://<server>/api/inbox -H "Authorization: Bearer lcc_…" -F "file=@scan.pdf"
  ```
  Die Antwort (`202`) enthält die Posteingangs-Einträge; der Eingang erscheint im Posteingang als „capture:<Name>".
* **Protokoll:** Anlegen und Widerruf stehen im Audit-Protokoll; der Upload wie jeder Posteingang. Abgelehnte Aufrufe erscheinen im Protokoll abgelehnter Anfragen.
* **Noch offen:** Metadaten je Upload (Stapel, Scanner, Seitenzahl), Erkennung doppelter Uploads, Eingangsweg im Posteingang anzeigen.
