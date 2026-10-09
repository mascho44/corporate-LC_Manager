# Recognition regression benchmark

The Training & Recognition quality area provides **Run synthetic recognition
benchmark**. The read-only JSON report is available under
`/api/training/document-types/benchmark`, subject to normal training access.

The fixed corpus in `src/main/resources/recognition-benchmark-v1.json` contains
11 synthetic cases / 14 pages: invoice, packing list, bill of lading, air waybill,
origin certificate, insurance certificate, numbered and reference-based
continuations, ambiguous headings, conflicting metadata and an unknown cover.
It contains no customer scans, licensed standards or tenant training records.

## What is measured

- Final proposed document types by page, with per-type one-vs-rest counts.
- Internal document boundaries: precision = correctly proposed boundaries /
  proposed boundaries; recall = correctly proposed boundaries / labelled boundaries.
  The final end-of-file page is not a document boundary. Empty denominators are
  reported as unavailable, never as 100%.
- Issue date, explicit LC reference and Original/Copy: exact value matches,
  missed values, false positives and correct negative cases. A wrong non-empty
  value counts as both a missed expected value and a false positive.
- Exact proposed split per case. Metadata is tested on the labelled spans,
  not the proposed spans; this deliberately isolates extraction from split errors.

Both the corpus and relevant compiled engine classes have SHA-256 fingerprints
in the report. Compare results only with their fingerprints and build context.
The same fixed engine/corpus result is cached until application restart; it
cannot change from user confirmations or cross-tenant data.

## Limits and next dataset

This is a deterministic **text-layer regression suite**, not a scan/OCR benchmark,
an independent holdout or a real-world accuracy percentage. It does not prove
that tenant training generalizes. Historical correction statistics remain a
separate display, and geometry/OCR tests remain separate test suites.

For meaningful production accuracy, collect a private consented corpus with
different issuers/layouts, scan quality, rotations, unknown pages, copies and
document types. Label spans and metadata before evaluation; separate training
and held-out cases by issuer/template, not merely by filename. Do not commit
customer PDFs or licensed source documents to the public repository. Report
coverage and failures by document type rather than only an overall percentage.
