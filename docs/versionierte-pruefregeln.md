# Versionierte Prüfgrundlagen

Der bestehende Regelkatalog dokumentiert Kennung, Version, Vergleichsgrundlage,
Erklärung und Grenzen einer automatischen Vorprüfung. Er ist kein vollständiges
UCP-/ISBP-Regelwerk und keine verbindliche Konformitätsentscheidung.

Neue manuelle Entscheidungen speichern die Katalog- und Regelversion sowie
einen JSON-Snapshot des automatischen Befunds. Ein SHA-256-Fingerabdruck bindet
die Entscheidung an Regelversion, automatische Bewertung, Nachricht,
LC-Bedingung, Dokumentname und erfassten Textbeleg sowie den vollständigen
LC- und Dokumentenstand einschließlich Dokumentinhalt. Auch ein ausgetauschtes
Dokument mit gleichem Namen macht eine erneute Prüfung erforderlich. Die Oberfläche übermittelt
den Fingerabdruck beim Bestätigen; der Server vergleicht ihn mit dem aktuellen
Befund. Veraltete oder nicht vorhandene Befunde werden nicht bestätigt.

Entscheidungen verschiedener Befundstände bleiben als eigene Datensätze
gespeichert. Änderungen an einer Entscheidung desselben Befundstands werden
nachvollziehbar protokolliert. LC-Änderungen markieren bisherige Entscheidungen
als ungültig, statt ihre gespeicherte Grundlage zu löschen. Die Historie ist
mit dem Recht `AUDIT_VIEW` unter
`GET /api/lcs/{lcId}/document-checks/decisions/history` abrufbar.
In der LC-Akte öffnet „Prüfentscheidungen ansehen“ diese Historie einschließlich
gespeicherter Regelversion, Begründung und Prüfgrundlage.
Die Audit-Schreibung erfolgt zusammen mit
der Entscheidung in einer Transaktion. Mehrere Bedingungen desselben Codes
und Dokumentnamens werden durch ihren Fingerabdruck unterschieden.

Alte Entscheidungen ohne gespeicherte Befundgrundlage bleiben erhalten,
überschreiben aber keine aktuelle automatische Bewertung. Für solche oder
geänderte Befunde erscheint ein Hinweis zur erneuten fachlichen Prüfung.
Snapshots sind Prüfbelege, keine vollständigen archivierten Dokumentensätze.
Der Fingerabdruck ist kein Manipulationsschutz und keine digitale Signatur.

Bei Änderungen an der Prüflogik ist die zugehörige Regelversion zu erhöhen.
Für noch nicht explizit katalogisierte interne Prüfungen muss mindestens die
Katalogversion erhöht werden. Neue fachliche UCP-/ISBP-Einzelregeln benötigen
eine geprüfte Grundlage und fachliche Freigabe; allgemeine ICC-Verweise allein
reichen hierfür nicht aus.
