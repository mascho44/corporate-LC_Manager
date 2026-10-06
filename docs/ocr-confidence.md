# OCR confidence

Scanned, image-only PDFs are rendered at 200 DPI (at most 20 pages) and processed
with Tesseract `deu+eng`, using both `txt` and `tsv` outputs. Digital PDFs keep
their text layer and have no measured OCR confidence.

The source format is documented at:
https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html

## Measurement and limitations

The application stores word confidence on a 0–1 scale, actual PDF page number,
pixel bounding box, render DPI, engine version and method
`TESSERACT_WORD_MIN_V2`. Bounding boxes are **not** PDF point coordinates.
The field score is the minimum word confidence; the arithmetic mean is shown
separately. Scores are not calibrated probabilities of correctness.

Field evidence is assigned only when the original field value matches one exact
full-token sequence, ignoring whitespace and a directly attached SWIFT tag such as
`:20:`. A standalone tag is not included in the value; a joined tag/value token
retains its whole-word measurement. Matching cannot cross another SWIFT tag.
No fuzzy character replacement is used.
Repeated values, transformed amounts, missing words or invalid confidence values
are reported as unavailable, not inferred as high confidence. Printed labels and
normalization/learning can therefore result in unavailable scores. A human must
still verify all training fields, including high-scoring fields.

Digital text uses `NOT_APPLICABLE`; old OCR imports without evidence and ambiguous
matches use `UNAVAILABLE`. Existing heuristic field mapping scores remain separate.
User corrections keep the measurement of the original value and do not create new
OCR measurements. Server-side preservation prevents the client replacing scores.

## Configuration

OCR processing permits 120 seconds per page by default, configurable via
`lc.ocr.page-timeout-seconds` (bounded to 1–300 seconds). Each document has a
900-second background processing budget including rendering, configurable through
`lc.ocr.document-timeout-seconds` (bounded to 60–1800 seconds). Page limits remain
independent; increasing the document budget does not disable them. Synchronous
training/preview extraction retains its previous maximum of 300 seconds.
A timed-out extraction is
reported as `OCR_TIMEOUT`, not as a missing OCR installation. The upload dialog
shows measured transfer progress and an indeterminate indicator during server
processing; it does not claim a measured OCR percentage.

`lc.ocr.confidence-threshold` (environment `LC_OCR_CONFIDENCE_THRESHOLD`) defaults
to `0.8`. Values must be between 0 and 1. The threshold at extraction time is stored
with the measurement; changing it applies to new imports, not historical evidence.
This is independent of `lc.extraction.confidence-threshold`, which governs the
heuristic field-mapping assessment.

## Storage and verification

Migration V36 adds `lc_document.ocr_evidence_json` and
`training_session.ocr_confidence_json`. Training evidence survives edits and is
included in JSON/XML exports. Document evidence is accessible at
`GET /api/documents/{id}/ocr-confidence` with the normal document access policy.
Existing imports are not silently reprocessed. Reimport a scan to measure it.

`OcrPipelineIntegrationTest` creates an image-only PDF and runs real Tesseract when
installed. It is skipped where Tesseract is absent; run it in an OCR-equipped test
environment before relying on production behavior. No production files are used.

## Isolated scan verification, 2026-10-05

A synthetic two-page image-only PDF was tested in the production runtime image
(Tesseract 5.5.2) with the updated V2 extraction classes, without network/database
access and without touching the running application. The first run exposed joined
tag/value tokens; V2 explicitly handles that case. The repeated run succeeded:
LC reference on page 1 scored 0.87557823; amount on page 2 scored 0.63511421 and
was flagged for review against 0.8. Word boxes and actual page numbers were
preserved. A separate UI smoke test verified visible word/page/pixel evidence and
HTML escaping. This is a synthetic pipeline test, not an authenticated production
upload or a calibrated accuracy benchmark.
