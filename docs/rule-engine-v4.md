# Schema 4: zusätzliche Fakten und sichere Bezugsbasis

Diese Erweiterung schließt technische Teil-Lücken, nicht sämtliche fachlichen UCP-/ISBP-Regeln. Das öffentliche Beispiel enthält nur eigene synthetische Tests. Private lizenzbezogene Packs und Quellenunterlagen bleiben außerhalb des Repositorys.

Ein einzelnes Pack darf bis zu 500 Regeln und 3000 synthetische Tests enthalten. Das Uploadlimit bleibt 5 MiB. Dadurch lassen sich kleinere private Module zu einem Pack zusammenführen, ohne Quellenunterlagen öffentlich zu speichern. Beim Zusammenführen müssen Regel-IDs und Testnamen eindeutig bleiben; alte separat aktive Module sind nach Aktivierung der gemeinsamen Version zu deaktivieren, um doppelte Befunde zu vermeiden.

## Bearbeitbare Angaben

Unter „Prüfdaten“ stehen jetzt zusätzlich Frachtführer, Unterzeichnerrolle, vertretenes Unternehmen, Lade-/Löschhafen, Abflug-/Zielflughafen, Schiff, Transportvermerk, ausgestellte Originalanzahl, Versicherungsdokumentart, Deckungsstrecke, Einheitspreis und Ursprungsland zur Verfügung. Im LC-Kontext werden entsprechende Ortsangaben, Mengen/Einheiten, Einheitspreise und finanzielle Bezugsbeträge erfasst. Speicherung benutzt weiterhin die vorhandenen JSON-Spalten, Rechte, CSRF-Prüfung, transaktionales Audit und Entwertung bestehender Prüfentscheidungen. Maximal 96 Felder bei unverändertem 64-KiB-Uploadlimit.

Die bloße Erfassbarkeit eines Feldes bedeutet nicht, dass sämtliche zugehörigen Regeln implementiert sind. Insbesondere Rollen, Vertretung, Schiffs-/On-board-Vermerke und Dokumentart erfordern noch fachliche Auswertung. Geografische Felder erlauben nur manuelle Kontextprüfungen: Synonyme, Routenvarianten und allgemeine Ortsangaben dürfen nicht allein aufgrund von Textungleichheit abgelehnt werden.

## Versicherungsbasis

Das abgeleitete, nicht überschreibbare Feld `LC_INSURANCE_BASE_AMOUNT` verwendet:

1. einen ausdrücklich erfassten, positiven `LC_CIF_CIP_VALUE_AMOUNT`, sofern vorhanden;
2. andernfalls den höheren Wert aus `LC_CLAIMED_AMOUNT` und `LC_GROSS_GOODS_AMOUNT`; beide müssen vorliegen und positiv sein.

Alle drei Eingaben müssen zuvor fachlich in der LC-Währung geprüft worden sein. Es gibt keine Währungsumrechnung und keine automatische OCR-Zuordnung dieser Basis. Eine ungültige explizite Basis fällt nicht unbemerkt auf die anderen Werte zurück. Fehlende Basis oder abweichende Versicherungswährung bleibt nicht prüfbar. Prozentsatz, vorrangige LC-Abweichungen und die tatsächliche Anwendbarkeit muss das Pack ausdrücklich konfigurieren. Es wird kein normativer Standardprozentsatz vorbelegt.

## Originalsatz und Toleranzen

`LC_DOCUMENT_ISSUED_ORIGINAL_COUNT` liest ausschließlich die erfasste Zahl ausgestellter Originale desselben Dokuments. Es kann mit `DOCUMENT_ORIGINAL_COUNT` verglichen werden, ohne andere Dokumenttypen zu vermischen. Originalqualität, erforderliches Original für Versender, Kopien und Sonderregeln bleiben manuell. Anzahl null/fehlend darf vom Pack nicht als bestehender vollständiger Originalsatz interpretiert werden; Bedingungen und negative Tests sind erforderlich.

LC-Menge und Einheitspreis ermöglichen konfigurierte Vergleiche mit Mengen-/Währungsschutz. Die normative Herleitung von Toleranzen aus Verpackungsart, Teilziehungen, Gesamtausnutzung, Einheitspreis und LC-Wortlaut ist damit noch nicht umgesetzt.

## Kompatibilität und offene Punkte

Schema 1–3 bleiben gültig. Neue Felder sind nur in Schema 4 erlaubt; ältere Befund-Fakten bleiben auf ihre ursprünglichen Feldbestände begrenzt, damit neue leere Felder ihre Review-Fingerabdrücke nicht ändern. Für Schema 4 werden bekannte Ausschlussbedingungen vor einem Dokument-fehlt-Befund geprüft. Unbekannter Kontext wird nicht als bestanden behandelt.

Offen bleiben vollständige dokumenttypbezogene Quellenabdeckung, LC-Vorrang-/Ausnahmeregeln, Prüfung von Bankzuständigkeit/Fristverlängerung/Zurückweisung, Raten-/Teilziehungskontext, detaillierte Transport- und Bescheinigungsprüfung sowie die fachliche Abnahme. Ein vollständiges privates ICC-Pack kann erst nach diesen weiteren Bausteinen belastbar fertiggestellt werden.
