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
The bounded alternative pass now optionally corrects orientation and small skew
as described below. The primary result remains the fallback.

The recognition-facts endpoint provides review-only metadata and exact word
locations where available. Normalized dates and copy labels may have no exact
token match; the UI explicitly states that instead of inventing a location.
Existing metadata review autosave and confirmed tenant-local training remain.

Synthetic regression fixtures cover numbered continuations, repeated references,
conflicts, legacy evidence and partial recovery. These prove behavior, not an
accuracy percentage on real scans. A representative labelled holdout corpus,
and broader layout generalization remain follow-up work.

## Scan geometry (2026-10-09)

Only weak/sparse OCR triggers preprocessing; digital PDFs and satisfactory OCR
avoid this extra work. Tesseract OSD can suggest 90/180/270-degree rotation when
orientation confidence is at least 5 (an engine heuristic, not a probability).
Missing OSD or uncertain output leaves orientation unchanged. The Docker runtime
includes [OSD data](https://pkgs.alpinelinux.org/package/v3.22/community/aarch64/tesseract-ocr-data-osd).

A projection-profile heuristic searches small skew from -5 to +5 degrees in
0.5-degree steps. It uses at most a 600-pixel sampled dimension, ignores blank
or excessive dark samples, and requires a 15% profile-score improvement.
Images are size-checked before decoding (20 million pixels) and transformed
output is subject to the same limit. Additional OCR remains inside the document
budget; OSD has a maximum 10-second subprocess budget.

Transforms expand the white canvas rather than cropping document content.
Inverse transforms map all four OCR rectangle corners back to the original
200-DPI raster, with bounded enclosing rectangles. The PDF itself is unchanged.
Only an alternative with no fewer words and better average engine confidence
replaces the primary result. Accepted correction angles appear in the inbox.
Page-local recognized text retains reading order on retry and after splitting,
independently of transformed word coordinates. Inbox list responses omit this
internal text; they expose progress and angle only.

Synthetic geometry tests cover 0/90/180/270 degrees, positive/negative small
skew, blank/upright pages, inverse bounds, untrusted orientation and memory limits.
CI now installs real Tesseract (German, English, OSD), so the existing real OCR
integration test no longer silently skips there. Local environments without
Tesseract still skip that test; simulated pipeline tests remain deterministic.
No general real-world accuracy or speed improvement percentage is claimed.
