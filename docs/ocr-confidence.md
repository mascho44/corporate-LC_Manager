# OCR confidence

Scanned, image-only PDFs are rendered at 200 DPI (at most 20 pages) and processed
with Tesseract `deu+eng`, using both `txt` and `tsv` outputs. Digital PDFs keep
their text layer and have no measured OCR confidence.

The source format is documented at:
https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html

## Measurement and limitations

The application stores word confidence on a 0–1 scale, actual PDF page number,
pixel bounding box, render DPI, engine version and method
`TESSERACT_WORD_MIN_V1`. Bounding boxes are **not** PDF point coordinates.
The field score is the minimum word confidence; the arithmetic mean is shown
separately. Scores are not calibrated probabilities of correctness.

Field evidence is assigned only when the original field value matches one exact
full-token sequence, ignoring whitespace. No fuzzy character replacement is used.
Repeated values, transformed amounts, missing words or invalid confidence values
are reported as unavailable, not inferred as high confidence. Printed labels and
normalization/learning can therefore result in unavailable scores. A human must
still verify all training fields, including high-scoring fields.

Digital text uses `NOT_APPLICABLE`; old OCR imports without evidence and ambiguous
matches use `UNAVAILABLE`. Existing heuristic field mapping scores remain separate.
User corrections keep the measurement of the original value and do not create new
OCR measurements. Server-side preservation prevents the client replacing scores.

## Configuration

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
