# Interne Rule Packs

Unter **Rule Packs** können Benutzer mit dem Recht `SETTINGS_MANAGE` eigene interne Prüfregeln als JSON importieren. Der Import aktiviert keine Regeln. Die mitgelieferten Beispieldaten sind synthetisch und keine bankfachliche Freigabe.

## Ablauf

1. Beispieldatei und JSON-Schema auf der Seite herunterladen.
2. Eigene Regeln, Versionsangaben, Quellenreferenzen und Testfälle ergänzen.
3. Datei auswählen und Vorschau sowie Testergebnisse prüfen.
4. Version importieren. Sie bleibt zunächst inaktiv.
5. Rechte und interne Freigabe ausdrücklich bestätigen und die Version aktivieren.

Aktivierungen gelten für alle Benutzer und LC-Akten bei nachfolgenden Prüfungen. Pro Pack ist höchstens eine Version aktiv. Eine frühere Version lässt sich nach erneuter Bestätigung wieder aktivieren; Deaktivieren schaltet das Pack aus. Gespeicherte Versionen werden nicht überschrieben. Import, Aktivierung und Deaktivierung werden gemeinsam mit der jeweiligen Änderung transaktional protokolliert.

## Zulässige Regeln

[Schema 2](rule-engine-v2.md) ergänzt Anwendungsbedingungen, Parteienvergleiche, strukturierte Prüfdaten und manuelle Warenprüfungen. Die folgenden Grundvergleiche stehen weiterhin unter Schema 1 zur Verfügung.

Das MVP vergleicht ausschließlich erfasste Dokumentbeträge, Dokumentwährungen und Dokumentdaten mit passenden LC-Werten. Als Vergleich stehen Gleichheit, Ungleichheit und numerische beziehungsweise Datumsgrenzen zur Verfügung. Ein Dokumentdatum ist nicht automatisch ein Versanddatum. Es werden keine Skripte, Formeln, Klassen oder externen Aufrufe ausgeführt.

Fehlende oder ungültige Werte ergeben eine Warnung „nicht prüfbar“, niemals ein positives Prüfergebnis. Jeder Regel müssen Testfälle für Erfolg, Abweichung und fehlende/ungültige Daten beiliegen. Nur Versionen mit vollständig bestandenen Tests können aktiviert werden; die Tests werden vor Aktivierung erneut ausgeführt.

Grenzen: 512 KiB pro JSON-Datei, 25 Regeln und 150 Testfälle pro Pack. Unbekannte Eigenschaften, doppelte JSON-Schlüssel und unzulässige Feld-/Operatorkombinationen werden zurückgewiesen.

## Nachvollziehbarkeit

Befunde enthalten Pack- und Regelversion sowie den Prüfsummenbezug. Prüfentscheidungen beziehen sich auf die konkreten Eingaben und die Regelversion. Ein Wechsel der Pack-Version übernimmt deshalb keine frühere Bestätigung stillschweigend.

## Rechte und Quellen

`OWN_INTERNAL` bezeichnet eigene interne Packs; `ICC_LICENSED` kennzeichnet ICC-/UCP-/ISBP-bezogene Packs. Beide können importiert werden. Vor Aktivierung bestätigt der berechtigte Benutzer eine ausreichende Lizenz beziehungsweise Nutzungserlaubnis und die fachliche Freigabe, einschließlich der Nutzung durch alle Benutzer dieser Installation. Die Bestätigung wird mit Benutzer, Herkunft, Version und Prüfsumme protokolliert. Die hochgeladenen ICC-PDFs bleiben privat und sind weder Bestandteil der Anwendung noch dieser Dokumentation oder des Beispiels.

Die Anwendung prüft die tatsächlichen Lizenzbedingungen nicht und erteilt keine rechtliche Freigabe. Der aktivierende Benutzer entscheidet eigenverantwortlich anhand seiner Rechte; eine persönliche Leselizenz deckt nicht notwendigerweise Software- oder organisationsweite Nutzung ab. Auch abstrahierte Regeln sind nicht automatisch lizenzfrei. Quellenreferenzen sind kurze Kennungen, keine kopierten Publikationstexte. Es werden keine ICC-Regeln oder Publikationen mitgeliefert.

## Schnittstellen

Alle Endpunkte liegen unter `/api/settings/rule-packs` und benötigen das Verwaltungsrecht. Änderungen benötigen zusätzlich den bestehenden CSRF-Schutz.

- `GET /`: gespeicherte Versionen und Aktivierungsstatus.
- `POST /preview`: JSON validieren und testen, ohne Speicherung.
- `POST /`: neue unveränderliche Version importieren.
- `POST /{id}/test`: gespeicherte Version erneut testen.
- `POST /{id}/activate`: mit `{"rightsConfirmed":true}` aktivieren.
- `POST /{packId}/deactivate`: Pack deaktivieren.

Die Datenbankmigration V44 speichert Versionen und die Auswahl aktiver/früherer Versionen. Die Engine läuft im bestehenden Dienst; ein separater Microservice ist noch nicht umgesetzt.
