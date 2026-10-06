# Gemischte Sammel-PDFs im Posteingang

Über „PDF aufteilen · Dokumenttypen prüfen“ werden Seiten einzeln anhand ihrer
Textschicht bzw. bereits gespeicherter OCR-Wörter klassifiziert. Dies ist eine
regelbasierte Vorschlagsfunktion, keine garantierte Dokumentgrenzenerkennung.
Typwechsel erzeugen neue Bereiche. Unklare Seiten werden einzeln vorgeschlagen;
Fortsetzungsseiten und mehrere Dokumente desselben Typs müssen gegebenenfalls
manuell zusammengefasst bzw. getrennt werden. Zwei Dokumente auf derselben Seite
können nicht durch diese seitenbasierte Funktion getrennt werden.

Vor dem Bestätigen können Von-/Bis-Seite und Dokumenttyp geändert werden.
Alle Seiten müssen genau einmal und in Reihenfolge abgedeckt sein. Es gelten
2–100 Bereiche, maximal 200 PDF-Seiten, 10 MB je Teil und 50 MB insgesamt.
Fehler führen zum Rollback; es werden keine halbfertigen Teile gespeichert.

Die Teile erscheinen als selbständige Dateien im Posteingang. OCR-Text und
Wortkoordinaten werden bereichsbezogen wiederverwendet und Seitennummern auf das
Teil-PDF umgerechnet. Referenz, Nummer und Betrag werden ausschließlich aus
dem Teiltext abgeleitet. Teile ohne Text kommen erneut in die Hintergrundqueue.
Die manuell bestätigten Typen bleiben auch nach dieser Erkennung erhalten.

Das Sammeloriginal bleibt mit Status `SPLIT` gespeichert und ist über die Teile
weiterhin erreichbar. Es wird nicht mehr als offene Datei zur Zuordnung angeboten.
Die Herkunft mit Seitenbereich bleibt am Inbox-Datensatz gespeichert; das Audit-
Ereignis `DOCUMENT_INBOX_SPLIT` verbindet Original, Teile, Seitenbereiche und Typen.
Erneutes Aufteilen desselben Originals wird abgewiesen. Die Teile können unabhängig
zugeordnet werden. Die Speicherung des Originals ist beim künftigen Löschkonzept
zu berücksichtigen.
