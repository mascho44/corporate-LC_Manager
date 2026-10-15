# HS-Code-Erkennung

HS-Codes (Zolltarif-/Warennummern) werden aus dem Text der Dokumente und aus der Warenbeschreibung des LC (Feld 45A bzw. erfasste Prüfdaten) gelesen – nur direkt hinter einer Beschriftung wie „HS Code“, „H.S. No.“, „Tariff No.“, „Commodity code“, „TARIC“, „Zolltarifnummer“, „Warennummer“. Ziffernfolgen ohne Beschriftung (Rechnungsnummern, Beträge) werden nicht als HS-Code gewertet. Punkte und Leerzeichen werden ignoriert (8471.30.00 = 84713000).

## Befunde
- `HS_CODE_OK` – Code im Dokument passt zum LC (gleiche Position ab 4 Stellen, der kürzere Code ist Anfang des längeren).
- `HS_CODE_MISMATCH` – Abweichung zum Code im LC.
- `HS_CODE_FOUND` – Code erkannt, LC nennt keinen.
- `HS_CODE_INCONSISTENT` – ohne Code im LC: Dokumente nennen untereinander abweichende Codes.
- `HS_CODE_EXPORT_CONTROL` (blockierend) – Warengruppe mit üblicher Exportkontroll- oder Sanktionsrelevanz: Waffen (Kap. 93), radioaktive Stoffe, Sprengstoffe, Kernreaktoren, Kampffahrzeuge, Luftfahrzeuge/Drohnen, Rohöl und Erdölerzeugnisse, Gold, Diamanten, Radar, Datenverarbeitungsmaschinen und integrierte Schaltungen.

Die Liste der Warengruppen ist ein Startpunkt und keine rechtliche Einstufung; Dual-Use-Güter lassen sich über HS-Codes nur grob erkennen.
