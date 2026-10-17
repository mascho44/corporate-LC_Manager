# OCR: Toleranz je Mandant, genauere Modelle, Formularfelder und Vergleich

Stand: 17.10.2026. Ergänzt [OCR-Konfidenz](ocr-confidence.md), [OCR-Verarbeitung](ocr-performance.md) und [Scan-Profil und OCR-Engine](scan-profile.md).

## OCR-Toleranz je Mandant
Mandanteneinstellungen → *Mandant* (Recht: Administrator mit Benutzerverwaltung und Zwei-Faktor): **Mindest-Sicherheit erkannter Wörter (%)**, erlaubt 50 bis 95, leer = Installationsstandard (`lc.ocr.confidence-threshold`, 80 %).
REST `GET/PUT /api/tenants/current/settings/ocr`, Audit `TENANT_OCR_TOLERANCE_UPDATED`, Spalte `tenant.ocr_confidence_threshold` (V116).

* Der Wert wird bei der Erkennung in den OCR-Beleg geschrieben und bestimmt, welche Werte als **unsicher** markiert werden.
* Er gilt für die **automatische Aufteilung** im Posteingang: Eine Seite zählt als sicher, wenn alle Wörter eine Konfidenz haben und deren **Mittelwert** die Toleranz erreicht (früher musste jedes einzelne Wort sie erreichen, was echte Scans mit Stempeln, Logos und Handschrift nie schaffen). Außerdem muss jede Seite eindeutig klassifiziert sein; Fortsetzungsseiten ohne Überschrift verhindern die Aufteilung weiterhin.
* Wirkt nur auf neu erkannte Dokumente; bereits erkannte behalten ihre Bewertung bis zur Neuerkennung. Niedriger = toleranter: mehr automatische Aufteilung und weniger Unsicher-Markierungen, dafür mehr Fehlerrisiko.

## Genauere Tesseract-Modelle im Image
Das Docker-Image ersetzt die schnellen Modelle der Alpine-Pakete für `eng`, `deu` und `chi_sim` durch die genaueren Modelle aus `tesseract-ocr/tessdata_best` (fester Commit, SHA-256 geprüft, Abbruch des Builds bei Abweichung; `osd` bleibt aus dem Paket).
Der Preis ist etwa die 2,5-fache OCR-Zeit je Seite; das Seitenlimit (`lc.ocr.page-timeout-seconds`, 120 s) bleibt.

## Zuordnung von Beschriftung und Wert (Formularfelder in Scans)
Scans von Formularen drucken die Beschriftung über dem Wert, mehrere Felder nebeneinander („Invoice No.: | Invoice Date: | Credit No." über „INV-1 | 05.12.2026 | LC-1"). Zeilenweise gelesen verlieren sie die Zuordnung.
`FormFieldPairing` ordnet sie über die OCR-Wortpositionen spaltenweise zu und hängt „Beschriftung: Wert"-Zeilen an den erkannten Text an (der Originaltext bleibt unverändert). Eine Spalte ohne Doppelpunkt neben einer Beschriftung zählt als eigene Spalte; „Invoice No." gilt nur für Handelsrechnungen.
Weitere Korrekturen für Scans: ein verlorenes Dezimalkomma („70.831 40 EUR") wird zu 70.831,40; der Referenz-Erkenner nimmt keine Werte aus Überschriftenzeilen und kein „LC" mitten in „TEST-LC-2026-003".

## Erkennung sauberer Dokumente
Erkannt werden unter anderem: Beträge mit „Amount", „Total amount payable", „Total (CIF)", „Gesamtbetrag", „Rechnungsbetrag", „Endbetrag", „Summe", den Zeichen € und £ und Apostroph als Tausendertrenner; Rechnungsnummern auch deutsch („Rechnungsnummer", „Rechnung Nr.", „Belegnummer"); die Nummern von Konnossement, Luftfrachtbrief/AWB, CMR, Packliste und Lieferschein (`DocumentNumberDetector`: nur mit „No./Nr./Number/#", der Wert enthält eine Ziffer, in der Folgezeile steht er allein); „Akkreditiv-Nr.", „LC-Nr.", „DC No."; „Place and date of issue: Hamburg, 05.12.2026".
Bewusst nicht erkannt: „05/12/2026" (als Tag/Monat oder Monat/Tag mehrdeutig). Ein Test (`CleanDocumentRecognitionTest`) hält die Formulierungen fest.

## Vergleich der OCR-Engines (Oktober 2026)
Gemessen auf einem Intel-Mac (CPU) mit fünf synthetischen Scans (Text bekannt, Anteil richtig gelesener Wörter, F1) und Stichproben auf drei echten Scans (Zahlen nachgeprüft, nur kleine Stichprobe, kein Beweis):

| Variante | Demo-F1 | Anmerkung |
|---|---|---|
| Tesseract schnell (vorher) | 0,970 | verlor bei echten Scans ein Dezimalkomma und las eine Rechnungsnummer falsch |
| **Tesseract „best" (jetzt)** | **0,981** | las Rechnungsnummer und Betrag richtig; ca. 2,5× Zeit |
| best + Vorverarbeitung (Gerade ziehen, Hintergrund glätten) | 0,970 | bei sauberen Scans kein Gewinn |
| Surya 0.4.5 | 0,968 (1 Seite) | gute Zahlen, aber 3–5 Minuten je Seite auf der CPU; Modellgewichte mit eingeschränkter Lizenz (Startups unter ca. 5 Mio. USD frei, sonst Vereinbarung nötig) |
| RapidOCR (PP-OCR, chinesisch-englisches Standardmodell) | 0,455 | Zeichen gut gelesen, aber ohne Leerzeichen zwischen den Wörtern („Totalamount:EUR125,000.00"), das bricht die beschriftungsbasierte Auswertung |

Auflösung 300 statt 200 dpi und andere Layout-Modi brachten auf den Demo-Scans höchstens 0,5 Punkte (Textblock-Modus schadet); die Anwendung rendert die OCR ohnehin mit dem Profil-DPI (Standard 300) und versucht Seiten zusätzlich mit einem zweiten Layout-Modus.
**Offen:** Eine echte Prüfmenge (10 bis 20 Seiten mit geprüften Werten) würde Entscheidungen über Engine, Auflösung und das gezielte Neulesen von Zahlenfeldern messbar machen.
