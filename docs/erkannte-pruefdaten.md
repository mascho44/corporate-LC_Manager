# Erkannte Prüfdaten: Packliste, Versicherungszertifikat, Adressen

LCM schlägt Prüfdaten aus dem erkannten Text (OCR) vor. **Nichts wird ohne Bestätigung gespeichert**, und bereits eingetragene Werte werden nie überschrieben.

## Was erkannt wird
- **Packliste:** Packstückzahl (mit Einheit, z. B. Kartons, Paletten, Kolli), Brutto- und Nettogewicht, Gewichtseinheit. Erkannt werden beschriftete Angaben
  („Total number of cartons: 48“, „TOTAL: 12 PALLETS“, „Anzahl Packstücke“, „Gross weight“, „G.W.“, „Bruttogewicht“; Zahlenformate `1,250.50` und `1.250,50`).
  Widersprüchliche Werte im Dokument ergeben **keinen** Vorschlag.
- **Versicherungszertifikat:** die **Policen-/Zertifikatsnummer** („Certificate No.“, „Policy No.“, „Policennummer“, „Zertifikatsnummer“, „Versicherungsschein-Nr.“) als Dokumentnummer,
  und die **Akkreditiv-Referenz** auch in der Form „Credit No./Credit Ref.“ (zusätzlich zu den bisherigen Beschriftungen wie „L/C No.“). Mehrere unterschiedliche Nummern bleiben unerkannt.
- **Adressen:** das **Land** der Käufer-/Auftraggeber- und der Verkäufer-/Begünstigtenadresse im Dokument (Block nach „Buyer/Applicant/Messrs/Sold to/Käufer“ bzw. „Seller/Beneficiary/Exporter/Verkäufer“;
  der Block endet an der nächsten Beschriftung oder Leerzeile) und auf **LC-Ebene** aus den SWIFT-Feldern `:50:` und `:59:` (Land aus der letzten Adresszeile; Name, Straße, PLZ/Ort getrennt).
  Länder werden als **englischer Name** vorgeschlagen (Namen auf Deutsch/Englisch, ISO-Codes in Großbuchstaben und gängige Abkürzungen wie „P.R. China“, „USA“, „UK“ werden erkannt).

## Wo es erscheint
- **Dokumente prüfen → „Erkannte Prüfdaten übernehmen“** (Recht `DOCUMENT_UPLOAD`): zeigt eine Vorschau (LC-Angaben und je Dokument die neuen Werte) und übernimmt nach Bestätigung **alle** Vorschläge für leere Felder.
  LC-Angaben nur mit dem Recht `LC_EDIT`. Bisherige Prüfentscheidungen der Akte werden dabei wie bei jeder Änderung der Prüfdaten zurückgesetzt; jede Änderung steht im Audit-Protokoll.
- **Prüfdaten-Dialog** eines Dokuments und **LC-Prüfkontext**: Vorschläge als Schaltflächen („… übernehmen“), Quelle im Tooltip.

## Grenzen
Bei zweispaltigen Layouts (Käufer und Verkäufer nebeneinander) kann die OCR die Spalten mischen; dann fehlt der Vorschlag oder er ist falsch. Deshalb nur als Vorschlag mit Quelle.
Die Länder werden als Text verglichen: Wer Länder von Hand einträgt, sollte denselben (englischen) Namen verwenden.

## Erweiterung: Akkreditivfakten aus MT700 und Dokument-Vermerke

**Akkreditiv (`Mt700Facts`)** – aus den strukturierten Feldern der SWIFT-Nachricht, jeweils mit Fundstelle als Quelle:
`:40E:` Regelstandard, `:39A:`/`:39B:` Toleranz, `:42A:` Bezogener, `:42C:` Tratten-Laufzeit und -Basis (auch „at sight“),
`:43P:`/`:43T:` Teilverladung/Umladung, `:44A:`/`:44B:`/`:44E:`/`:44F:` Orte und Häfen (Flughäfen nur, wenn ausschließlich ein Air Waybill gefordert ist),
`:45A:` Warenbeschreibung und eindeutiger Incoterm, `:46A:`/`:47A:` Versicherungsprozent, Risiken, Frachtbedingung, On-Board-Vermerk, `:48:` Vorlagefrist.
Mehrdeutige Angaben (z. B. mehrere Incoterms, ungleiche Toleranzen) werden nicht oder nur mit Hinweis vorgeschlagen.

**Dokumente (`DocumentFactSuggester`)** – Schiff, Übernahme-/Zielort, Flughäfen (Air Waybill), Frachtvermerk (prepaid/collect), Incoterm (nicht auf Konnossementen),
Ursprungsland (nur wenn als Land erkannt) sowie beim Versicherungszertifikat Versicherungssumme, Währung und Risiken.

**Vermerke (`DocumentIndicators`)** – die `*_INDICATED`/`*_PRESENT`-Fakten (Umladung, Charter Party, On Deck, To Order, Container, Agent, Rabatt, Prämie bezahlt, Franchise, Indossament u. a.)
werden nur bei **gefundenem** Textmuster als „true“ vorgeschlagen; ein fehlender Text beweist nichts und erzeugt keinen Vorschlag. Alle Vorschläge werden erst nach Bestätigung gespeichert.

## Aussteller, Empfänger und Warenbeschreibung aus dem Druckbild
Für diese drei Freitextfelder schlägt die Regel-Erkennung (ohne KI) vor:
* **Aussteller:** eine beschriftete Zeile („Seller:", „Issued by", „Aussteller", „Lieferant" …), sonst die erste Zeile mit Firmenform (GmbH, AG, Ltd, Limited, Co., Inc …) in den ersten 12 Zeilen (Briefkopf).
* **Empfänger:** eine beschriftete Zeile („Buyer", „Sold to", „Bill to", „Consignee", „Rechnungsempfänger" …), sonst die nächste Firma nach dem Aussteller.
* **Warenbeschreibung:** die Zeile(n) hinter „Description of goods", „Warenbezeichnung" …, sonst die Wörter der ersten Artikelzeile unter einer Tabellenüberschrift mit Artikel-/Beschreibungs- und Mengenspalte (Codes, Zahlen und Einheiten entfallen).
Widersprüchliche Beschriftungen ergeben keinen Vorschlag. Vorschläge aus Position und Tabelle sind mit „bitte prüfen" gekennzeichnet und wie alle Vorschläge unbestätigt.

## Fundstelle im PDF markieren (statt Kommentar)
In der Belegansicht eines Befunds gibt es „Fundstelle markieren": ein Rechteck um die Stelle im PDF aufziehen (Maus oder Finger). Wird der Befund danach mit „Als erfüllt bestätigen" abgeschlossen, ist **kein Kommentar** mehr nötig; die Markierung (Dokument, Seite, Rechteck als Anteil der Seite) wird mit der Entscheidung gespeichert (`document_check_decision.evidence_mark`). Ohne Markierung bleibt die Begründung Pflicht. Beim erneuten Öffnen erscheint die gespeicherte Fundstelle grün auf der Seite; in der Entscheidungshistorie steht „Fundstelle im PDF markiert (Seite n)". Der Server prüft, dass das Dokument zur Akte gehört. Im PDF-Prüfbericht steht die Markierung noch nicht.

## Dokumentweise Prüfung
In der Prüfung der Akte (Recht „Dokumente prüfen") öffnet **„Dokumentweise prüfen"** einen Dialog je Dokument: rechts das PDF, links alle Befunde zu diesem Dokument untereinander (offene Abweichungen vor Hinweisen, Erledigtes grau). Der aktive Befund springt im PDF auf seine Fundstellen-Seite. Pro Befund „Als erfüllt bestätigen" / „Abweichung bestätigen"; mit einer im PDF markierten Fundstelle (siehe oben) entfällt der Kommentar, sonst fragt das System wie sonst nach der Begründung. Nach jeder Entscheidung springt der Dialog zum nächsten offenen Befund, nach dem letzten zum nächsten Dokument mit offenen Befunden; mit ← und → lässt sich auch von Hand zwischen den Dokumenten wechseln. Befunde zum ganzen Dokumentensatz (ohne einzelnes Dokument) stehen am Ende ohne PDF. Die Reihenfolge der Dokumente ist die der Dokumentenliste der Akte.
