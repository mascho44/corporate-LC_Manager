# Gemischte Sammel-PDFs im Posteingang

Über „PDF aufteilen · Dokumenttypen prüfen“ werden Seiten einzeln anhand ihrer
Textschicht bzw. bereits gespeicherter OCR-Wörter klassifiziert. Dies ist eine
regelbasierte Vorschlagsfunktion, keine garantierte Dokumentgrenzenerkennung.
Typwechsel erzeugen neue Bereiche. Unklare Seiten werden einzeln vorgeschlagen;
Fortsetzungsseiten und mehrere Dokumente desselben Typs müssen gegebenenfalls
manuell zusammengefasst bzw. getrennt werden. Zwei Dokumente auf derselben Seite
können nicht durch diese seitenbasierte Funktion getrennt werden.

Vor dem Bestätigen können Von-/Bis-Seite und Dokumenttyp geändert werden.
Alle Seiten müssen genau einmal und in Reihenfolge abgedeckt sein. Es gelten
2–100 Bereiche, maximal 200 PDF-Seiten, 10 MB je Teil und 50 MB insgesamt.
Fehler führen zum Rollback; es werden keine halbfertigen Teile gespeichert.

Die Teile erscheinen als selbständige Dateien im Posteingang. OCR-Text und
Wortkoordinaten werden bereichsbezogen wiederverwendet und Seitennummern auf das
Teil-PDF umgerechnet. Referenz, Nummer und Betrag werden ausschließlich aus
dem Teiltext abgeleitet. Teile ohne Text kommen erneut in die Hintergrundqueue.
Die manuell bestätigten Typen bleiben auch nach dieser Erkennung erhalten.

Das Sammeloriginal bleibt mit Status `SPLIT` gespeichert und ist über die Teile
weiterhin erreichbar. Es wird nicht mehr als offene Datei zur Zuordnung angeboten.
Die Herkunft mit Seitenbereich bleibt am Inbox-Datensatz gespeichert; das Audit-
Ereignis `DOCUMENT_INBOX_SPLIT` verbindet Original, Teile, Seitenbereiche und Typen.
Erneutes Aufteilen desselben Originals wird abgewiesen. Die Teile können unabhängig
zugeordnet werden. Die Speicherung des Originals ist beim künftigen Löschkonzept
zu berücksichtigen.

## Datum und Unterschrift beim Aufteilen
Je Bereich gibt es im Aufteilen-Dialog ein Feld „Datum, falls nicht erkannt" und das Kästchen „Unterschrift vorhanden" (nicht beim reinen Training). Die Angaben stehen am entstehenden Posteingangs-Dokument (`manual_document_date`, `signature_confirmed`). Beim Zuordnen zu einer Akte wird das Datum als Dokumentdatum übernommen, wenn im Zuordnungsformular kein anderes steht (das Formular ist damit vorbelegt, „Beim Aufteilen eingetragen"), und die bestätigte Unterschrift wird als Prüfdatum `DOCUMENT_SIGNED = true` am Dokument gespeichert. Das ist eine menschliche Bestätigung; erkannt wird die Unterschrift weiterhin nur aus dem Dokument selbst.

## Sicherheit je Seite und unsichere Bereiche (Stand 15.10.2026)
* Jede Seite zeigt **„sicher"** oder **„prüfen"** mit Grund: Überschrift (mit Text), vom LC gefordert, Seitenzählung, Seitenanzahl der Vorseite, gleiche Dokumentnummer, Leer- oder Rückseite, typische Felder, bekanntes Layout, ähnlich bestätigten Beispielen, bestätigtes Training, nur Dateiname, mehrere Dokumentarten möglich. Der Grund steht als Tooltip an der Seitenvorschau (unsichere Seiten orange umrandet) und je Bereich als Zeile.
* Bei gemischten Vorschlägen: „X von Y Bereichen sind sicher erkannt und werden so übernommen" und der Schalter **„Nur unsichere Bereiche zeigen"**. Bestätigt wird weiterhin die ganze Aufteilung.
* Seitenzählung wird erkannt als „Page 1 of 3", „Seite 2 von 3", „Blatt 2 von 3", „Page No. 1/2".
