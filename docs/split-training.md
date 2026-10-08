# Confirmed PDF split patterns

## Pretraining

On Training & Document Recognition, choose **Sammel-PDF vortrainieren**, upload a PDF (up to 10 MB), review page ranges and types, then save the pattern. This initial pretraining workflow is for multi-page bundles with two or more ranges, not single-document classification. No inbox items, LC or original PDF are saved. Uploading/cancelling does not train. Confirmation requires TRAINING_MANAGE and records an atomic audit event.

OCR/extraction runs once during preview. Confirmation uses a signed, user/tenant-bound receipt (one-hour validity, invalidated by server restart) rather than reprocessing the PDF. The original remains in the browser only for the current review. Synchronous extraction retains the existing 300-second ceiling; large scans can still time out. Background pretraining is a follow-up, not included here.

Confirming a manual split stores a tenant-local training example in PostgreSQL, in the same transaction as the split. Editing inputs alone, cancelling and automatic splits do not train the system.

Examples retain a SHA-256 fingerprint of the normalized, ordered page text, confirmed page ranges and document types, reviewer and timestamp. They do not copy the original PDF or OCR text. Deleting inbox documents does not delete these examples. Tenant test-data purge includes them and inventory reports them.

When reviewing another bundle with the same normalized page sequence, the saved ranges and types become the proposed split. Case, whitespace and numeric runs are normalized; wording and page order must otherwise match. Every page must contain at least 40 letters. Blank/unreadable pages and differing templates do not match. Conflicting examples suppress learned suggestions rather than selecting a winner.

All learned suggestions are marked REVIEW and require human confirmation. They never qualify for automatic splitting. This is conservative template replay, not statistical model training or a calibrated confidence. It does not yet generalize to arbitrary layouts, variable page counts, or OCR spelling differences. The existing rules remain the fallback. Existing historical splits are not retroactively imported.
