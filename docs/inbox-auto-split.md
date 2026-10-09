# Automatic combined-PDF splitting — Issue #113

New inbox PDF uploads are extracted by the existing durable background queue.
After extraction, different clearly recognized document types are automatically
separated into independent inbox PDFs. The original is retained with status SPLIT;
each child records its source inbox ID and inclusive original page range.
Source links remain available from the children. No LC assignment is performed.
Migration V72 also fixes the existing database constraint: V48 had introduced
split lineage, but V32 still allowed only OPEN/ATTACHED, preventing retained
sources from being marked SPLIT in PostgreSQL. Existing migrations are unchanged.

Automatic separation requires at least two groups and two different types, with
every page classified as SUGGESTED with rule score >= 0.9. These are heuristic
classification scores, not calibrated probabilities. Adjacent pages with the same
recognized type remain together. Unknown/ambiguous pages, continuation pages
without recognized headings, single-type documents and unsupported layouts do not
split automatically. They retain the existing manual page-range review.
The supported types follow DocumentClassifier, including invoice, packing list,
transport/insurance/origin documents, advising letters and structured MT700 pages.
Different documents of the same type are not automatically separated.

For OCR_EXTRACTED scans, every proposed page must have OCR word evidence and all
recorded confidences must be finite, present and >= 0.8. Partial OCR coverage or
uncertain recognition prevents automatic splitting. Existing OCR page/time limits
still apply. OCR is not rerun for child PDFs; text, words and coordinates are
copied only from the corresponding pages, with page numbers rebased to one.
Limits remain 100 parts, 10 MB per generated part and 50 MB total, in addition to
the existing PDF page/resource checks. No arbitrary document-count guess is made.

The background worker prepares PDF outputs outside the database transaction. Its
completion transaction rechecks the original extraction lease, tenant ownership,
OPEN status and manual classification. Child insertion, original status and split
audit/outbox records commit atomically. An audit/persistence failure rolls back
the split; the existing stale extraction lease can be reclaimed after 35 minutes.
Deleted, reassigned, manually classified or previously split originals cannot be
overwritten by late results. Child documents are not recursively auto-split.

Automatic classifications remain suggestions, not human-confirmed training data.
Children show an automatic-split notice; assignment/type approval still requires
the normal user action. No automatic learning confirmations are created.
Queued workers enumerate active tenants only; already-running work is subject to
the existing lifecycle limitations described in tenant-lifecycle.md.

For existing processed PDFs, **Split automatically** invokes
`POST /api/inbox/{id}/auto-split` with DOCUMENT_UPLOAD permission and CSRF
protection. No upload is necessary. The endpoint locks the original, prepares a
bounded proposal and persists it atomically. An empty result means manual review
is needed; failed requests leave the original available. Audit attributes this
explicit action to its requester; background splitting follows the upload actor.
Configure `lc.inbox.auto-split-enabled=false` to disable automatic background
splitting while retaining the explicit action and manual range review.

If background completion produces new children while another assignment is being
edited, the UI signals a refresh instead of overwriting those edits. EN and DE
labels are provided. The feature does not claim perfect segmentation or split
multiple documents sharing a single page.

## Verification

### Multi-page proposal refinement

Split proposals recognize consecutive explicit `Page n of total`, `Page n / total`
and German `Seite n von total` markers. An otherwise unknown nonblank page may
inherit the preceding document type only when the sequence and total match and
there is no conflicting document reference. Such inheritance is marked REVIEW
with PAGE_SEQUENCE_V1 evidence, not high-confidence automatic classification, so
it never bypasses the automatic splitter's conservative acceptance gate.
Numbering restarting at one or a changed invoice/packing-list number creates a
new proposed part even when the document type stays the same. Missing, skipped or
contradictory numbering, blank pages and ambiguous type evidence remain separate.
These are heuristic proposals; unnumbered continuation inference is not yet
implemented. The source PDF is retained and all ranges remain editable.

Local verification passed: 562 Java tests (two skipped), 116 JavaScript tests,
and the isolated PostgreSQL acceptance workflow through migration V72. Coverage
includes retained original bytes, page ranges, tenant isolation, atomic rollback,
retry without duplicate parts, uncertain-page fallback and manual splitting.
Only synthetic PDFs were used. This verification does not constitute a production
deployment.

## Neu erkennen (Bestandsdokumente)

The recognition rules (OCR at 300 DPI, dates, stamps, document types, signatures) only applied to new uploads.
Two entry points run the current recognition again:

* **Stored documents of a dossier:** button "Neu erkennen" per document and "Alle neu erkennen" in the document list
  (permission: document upload). `POST /api/lcs/{lcId}/documents/{id}/re-recognition` recognises the stored file
  (up to 10 pages) and returns a before/after list **without storing anything**; `.../re-recognition/apply` with the
  returned token (single use, 15 minutes, bound to user, document and dossier) stores it. Machine-derived values
  (status, text, evidence, number, reference, recognised amount/currency) are replaced; human-maintained values
  (date, mark, amount, currency) are only filled when empty; the document type is only suggested. The dossier check
  decisions are reset (as for other document data changes) and the change is audited as `DOCUMENT_RE_RECOGNIZED`
  with before/after.
* **Inbox items:** "Neu erkennen" appears for finished items (`POST /api/inbox/{id}/retry?force=true`) and re-queues the
  recognition; user entries in the form remain.
