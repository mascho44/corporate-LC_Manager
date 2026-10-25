# Scan-Profil und OCR-Engine

Die Aufbereitung eingescannter Seiten ist von der Erkennung getrennt, damit später andere Scanner oder Engines eingesetzt werden können,
ohne den Rest der Anwendung anzufassen.

## Engine (`OcrEngine`)
Schnittstelle: Bild und Optionen hinein, Seitentext und Wörter mit Rahmen und Konfidenz heraus. Der Rest der Anwendung kennt nur das normierte
`OcrEvidence`, nie Tesseract. `TesseractEngine` ist der einzige Adapter (Text aus `txt`, Positionen aus `tsv`, zweiter Versuch mit `--psm 11` auf der
entzerrten Seite, Orientierungserkennung über `osd`). Die Prozessausführung (Zeitlimits, Protokollierung) bleibt im `DocumentExtractionService`.
Eine weitere Engine wäre ein weiterer Adapter mit derselben Schnittstelle.

## Profil (`ScanProfile`), je Mandant
Unter *Mandant → Scan-Profil* (Recht `SETTINGS_MANAGE`) wählbar. Ohne Auswahl gilt **Standard**.

| Profil | Render-DPI | Binarisierung | gedacht für |
|---|---|---|---|
| Standard | 300 | adaptive Schwelle (Sauvola) | bisheriges Verhalten; gemischte Scans und Uploads |
| Profi-Scanner | 300 | keine zusätzliche (Tesseract-Standard) | saubere Graustufen-Scans |
| Schlechter Scan | 400 | adaptive Schwelle (Sauvola) | kleine oder blasse Schrift, langsamer |

**Standard ist unverändert** zum Verhalten vor der Einführung der Profile (durch die vorhandenen Regressionstests abgesichert). Die Werte von
*Profi-Scanner* und *Schlechter Scan* sind **Startwerte**, die mit echten Scans des Zielgeräts zu kalibrieren sind (Vergleich mit dem Erkennungs-Benchmark).

- Die Änderung gilt für ab jetzt erkannte Dokumente; vorhandene werden über **Neu erkennen** mit dem neuen Profil erkannt.
- Wortpositionen werden unabhängig vom Profil immer im 200-DPI-Raster gespeichert (Ausschnitte und Fundstellen bleiben vergleichbar).
- Bei einem nicht-standardmäßigen Profil trägt der Nachweis die Methode `TESSERACT_WORD_MIN_V2+<PROFIL>`, damit eine Erkennung nachvollziehbar ist.
  Standard behält `TESSERACT_WORD_MIN_V2`.
- Ist die Profilabfrage nicht möglich (z. B. Datenbankfehler), wird mit Standard weitergearbeitet, die Erkennung stoppt nie wegen des Profils.
- Audit: `SCAN_PROFILE_CHANGED` mit altem und neuem Profil.

## Geplant
Eingangsanalyse (`AUTO`: eingebettete Auflösung, Farbtiefe, vorhandene Textebene), Umgang mit der Textebene von Scannern (übernehmen, prüfen, neu erkennen) und
ein Profil-Vergleich im Benchmark.
