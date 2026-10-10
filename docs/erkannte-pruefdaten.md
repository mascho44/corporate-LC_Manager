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
