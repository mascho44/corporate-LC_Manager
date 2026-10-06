# Hintergrundverarbeitung im Dokumentenposteingang

Uploads werden validiert und mit Status `QUEUED` in PostgreSQL gespeichert.
Die HTTP-Anfrage bestätigt die Speicherung mit `202 Accepted` und wartet nicht
mehr auf Textextraktion oder OCR. Das gilt auch
für ZIP- und Mehrfachimporte; jede entpackte Datei erhält einen eigenen Auftrag.

Ein dedizierter Hintergrund-Thread übernimmt die Aufträge einzeln. Die
Datenbanktransaktionen beschränken sich auf Übernahme und Ergebnisspeicherung;
OCR blockiert weder den Scheduler noch eine laufende Datenbanktransaktion.
Dateien erhalten während der Verarbeitung `PROCESSING`, anschließend den
vorhandenen Extraktionsstatus wie `EXTRACTED`, `OCR_EXTRACTED` oder `OCR_TIMEOUT`.

Der Browser aktualisiert sichtbare laufende Aufträge alle vier Sekunden.
Bereits ausgefüllte Zuordnungsformulare werden dabei nicht überschrieben.
Originaldateien können sofort angesehen oder gelöscht werden. Zuordnung und
Aktenanlage sind erst nach Abschluss der Verarbeitung möglich.

Fehlgeschlagene Erkennung lässt sich über **Erkennung erneut starten** ohne
erneuten Upload wiederholen (`POST /api/inbox/{id}/retry`, Upload-Berechtigung
und CSRF-Schutz). Erfolgreiche Dateien werden nicht automatisch erneut gelesen.

Nach einem Prozessabbruch bleiben Aufträge gespeichert. Verwaiste
`PROCESSING`-Aufträge können nach 35 Minuten neu übernommen werden. Dieser
Zeitraum liegt über dem maximalen Dokumentzeitbudget von 30 Minuten. Ein
Auftragstoken verhindert, dass ein alter Worker das Ergebnis eines neu
übernommenen Auftrags überschreibt. Löschen während OCR führt nicht zur
Wiederherstellung der Datei. Abschluss und erneute Einreihung werden auditiert.

Die bisherigen Dateigrößen-, Seiten-, Parallelitäts- und OCR-Zeitlimits bleiben
aktiv. Hintergrundverarbeitung garantiert keinen fachlich erfolgreichen
OCR-Lauf; sie verhindert, dass dessen Dauer den Upload-Aufruf offen hält.

Migration: `V47__inbox_background_extraction.sql` ergänzt Übernahmezeit,
Auftragstoken und einen Queue-Index, ohne bestehende Dokumente zu verändern.
