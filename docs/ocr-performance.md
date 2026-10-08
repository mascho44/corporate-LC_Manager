# OCR processing

PDF extraction now selects OCR pages individually. Embedded page text is retained;
only blank-text pages are rasterized and passed to Tesseract. Consecutive scan
pages are rendered together to avoid repeatedly loading a fully scanned PDF.
Original page numbers are preserved in OCR confidence/coordinate evidence.
Mixed PDFs no longer silently skip image-only pages just because another page
contains embedded text. Nonblank but unusable text layers are not yet detected.

Tesseract processes use OMP_THREAD_LIMIT=1 to avoid nested CPU oversubscription
when multiple documents are processed. DPI remains 200, languages remain deu+eng,
and confidence thresholds and process/document timeouts remain unchanged.
The OCR limit defaults to 100 scanned pages per document, configurable with
`lc.ocr.max-pages` (clamped to 1–200). Rendering uses blocks of at most three scan
pages, limiting temporary images independently of the full document size.
PDFs exceeding the scan-page limit report OCR_PAGE_LIMIT rather
than reporting a silently truncated result as complete. Temporary OCR artifacts
are removed, including on failure. No cross-user or cross-tenant cache is added.

Timing logs contain extraction status, elapsed duration and OCR/text page counts,
never filenames, recognized text or credentials. These allow operational
measurement; no speedup factor has been established. Page-parallel OCR, persistent
progress and reusable recognition caching are not part of this change.
