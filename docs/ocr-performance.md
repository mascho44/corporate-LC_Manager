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
progress and reusable recognition caching were initially not part of this change.

## Page recovery and review (2026-10-09)

Completed OCR pages now retain explicit page status and word positions in the
existing tenant-owned evidence JSON. The inbox worker checkpoints evidence and
text after each scanned page, guarded by its claim token and the open item state.
Deleted items and stale workers cannot overwrite a new claim.
Retries reuse only pages explicitly marked OCR_EXTRACTED; legacy evidence without
completion markers is not trusted as a complete result. Digital page text wins.
Partial results are labelled OCR_PARTIAL and never automatically split.
The inbox displays completed/total pages and page-local errors. Pending pages
after document-budget exhaustion remain pending and can be retried.

Sparse or low-confidence OCR receives one bounded alternative pass (PSM 11).
It replaces the primary result only if it retains at least as many words and has
a higher mean OCR confidence. These are heuristics, not proof of accuracy.
No rotation or deskew is applied yet: transformed coordinates would also need
to be mapped correctly into previews before such preprocessing is safe.

The recognition-facts endpoint provides review-only metadata and exact word
locations where available. Normalized dates and copy labels may have no exact
token match; the UI explicitly states that instead of inventing a location.
Existing metadata review autosave and confirmed tenant-local training remain.

Synthetic regression fixtures cover numbered continuations, repeated references,
conflicts, legacy evidence and partial recovery. These prove behavior, not an
accuracy percentage on real scans. A representative labelled holdout corpus,
rotation/deskew and broader layout generalization remain follow-up work.
