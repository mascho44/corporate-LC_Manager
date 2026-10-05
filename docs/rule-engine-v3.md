# Rule Engine: Schema 3

Schema 3 erweitert die generische Prüfengine, ohne einen ICC-Regelkatalog auszuliefern. Schema 1 und 2 bleiben importierbar. Das öffentlich enthaltene Beispiel `rule-pack-example-v3.json` enthält ausschließlich eigene synthetische Regeln und Testwerte; Prozentsätze, Fristen und Kalendertage sind keine UCP-/ISBP-Vorgaben.

## Weitere Bausteine

- Dokumentübergreifende Vergleiche: Beträge, Währungen, Versanddatum, Mengen und Gewichte. Der konfigurierte Gegen-Dokumenttyp muss eindeutig vorhanden sein. Bei mehreren Präsentationen wird eine gemeinsame Präsentationskennung in den Prüfdaten verwendet. Mehrdeutige oder fehlende Gegen-Dokumente bleiben nicht prüfbar.
- Versicherung: separate Angaben für versicherten Betrag, Versicherungswährung und Deckungsbeginn. Mindestdeckung wird gegen einen konfigurierten Bezugsbetrag geprüft. Versicherungsklauseln und Risiken bleiben eine manuelle fachliche Prüfung, auch bei identischem Wortlaut.
- Toleranzen: exakte Dezimalrechnung, konfigurierbare Prozentsätze und Grenzen. Es gibt keine automatische Währungs- oder Einheitenumrechnung. Fehlende oder abweichende Währungen und Einheiten ergeben `NOT_EVALUABLE`.
- Fristen: `WITHIN_DAYS` zählt ab dem Tag nach dem Startdatum bis einschließlich Enddatum. Ohne Kalender sind es Kalendertage; mit Kalender werden konfigurierte geschlossene Wochentage und einzelne Schließtage ausgeschlossen. Kalender müssen den gesamten Prüfzeitraum abdecken. Keine automatische Feiertagsermittlung oder Annahme einer Bankzuständigkeit. Start und Ende müssen fachlich erfasst werden.
- Unterschriften und Originalanzahl: Anforderungen werden separat pro Dokumenttyp im LC gespeichert. Unbekannter Unterschriftsstatus ist nicht gleich „nicht unterschrieben“. Eine Anforderung für einen Transportbeleg gilt nicht automatisch für Rechnungen oder Versicherungsdokumente.

## Bearbeitung und Sicherheit

„Prüfdaten“ am Dokument öffnet die dokumentbezogenen Angaben. „LC-Prüfkontext“ enthält allgemeine Angaben; „LC-Anforderungen für diesen Typ“ die typbezogenen Anforderungen. Speichern benötigt die vorhandenen Dokument- bzw. LC-Bearbeitungsrechte und CSRF-Schutz. Eingaben sind typisiert und größenbeschränkt (64 KiB). Eine Änderung wird zusammen mit dem Audit-Eintrag gespeichert und macht bestehende Prüfentscheidungen ungültig. Ein Audit-Fehler rollt die Änderung zurück.

Gegen-Dokument-ID, Dateiname und Inhaltsfingerabdruck fließen in den Befund ein. Damit ändern sich Review-Fingerabdrücke bei Änderungen am Vergleichsdokument. Pack-Versionen bleiben unveränderlich; Aktivierung erfordert erfolgreiche mitgelieferte Tests und die Bestätigung der Nutzungsrechte.

## Grenzen und nächste fachliche Schritte

Diese Erweiterung ist kein vollständiges ICC-Pack und keine Garantie normgerechter Dokumentenprüfung. Fachlich freigegebene Regel-Packs können privat importiert werden; Quellen-PDFs und lizenzierte Pack-Inhalte werden nicht in das öffentliche Repository aufgenommen. Kalender, Mengenbasis, Versicherungsumfang und Fristanfang müssen fachlich korrekt konfiguriert werden. Semantische Klauselprüfung, automatische Faktengewinnung aus allen Dokumenttypen, lokale Bankpraxis und vollständige normative Abdeckung bleiben gesonderte Aufgaben. Menschliche Prüfung bleibt erforderlich.

Die JSON-Schema-Datei unterstützt die Strukturprüfung. Die verbindlichen Prüfungen von Feldpaaren, Parametern, Kalenderbereichen und erforderlichen Testfällen erfolgen zusätzlich beim Import im Backend.
