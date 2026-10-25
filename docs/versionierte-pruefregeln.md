# Versionierte Prüfgrundlagen

Das interne Prüf- und Audit-Framework mit eigenen [Rule Packs](internal-rule-packs.md)
ist die Grundlage der Prüfung; die Zielarchitektur einer eigenständigen Engine
steht in [lc-rule-engine](lc-rule-engine.md).

Der bestehende Regelkatalog dokumentiert Kennung, Version, Vergleichsgrundlage,
Erklärung und Grenzen einer automatischen Vorprüfung. Er ist kein vollständiges
vollständiges Regelwerk und keine verbindliche Konformitätsentscheidung.

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
Katalogversion erhöht werden. Neue fachliche Einzelregeln benötigen
eine geprüfte Grundlage und fachliche Freigabe; allgemeine Verweise allein
reichen hierfür nicht aus.

## Bindung von Prüfentscheidungen (Stand 15.10.2026)
Eine Entscheidung gilt für den Befund (Regel, Text, Vergleichswerte), **das betroffene Dokument** (Inhalt, erkannter Text, Prüfdaten) und die **Akkreditivbedingungen** (Parteien, Betrag, Fristen, Dokumentenanforderungen, Rohtext, Prüfdaten und Rule Packs der Akte). Bei Befunden zum Dokumentensatz zählt nur, welche Dokumente welcher Art vorliegen. Ein weiteres Dokument, eine Zuweisung, ein Statuswechsel, Notizen oder Wiedervorlagen lassen Entscheidungen unberührt; Upload, Zuordnung aus dem Posteingang, Neuerkennung und Prüfdaten-Änderungen setzen nicht mehr alle Entscheidungen zurück – veraltet wird nur, was sich wirklich geändert hat. Entscheidungen aus der Zeit der früheren Bindung an die gesamte Akte gelten weiter, solange sich nichts geändert hat.
