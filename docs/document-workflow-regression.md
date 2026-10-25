# Document workflow regression

Run `bash scripts/test-tenant-workspaces.sh` with Docker, curl and Node.js 20+.
The runner builds a disposable localhost installation with PostgreSQL, Poppler,
Tesseract and synthetic credentials. Its unique Docker project and volumes are
removed afterward. Never run the API test directly against production.

The existing CI tenant acceptance job automatically runs these checks:

- Async pretraining returns HTTP 202, then a completed job and signed receipt.
- Invalid confirmation coverage cannot save training or create inbox documents.
- Jobs and rendered document pages are isolated between tenants.
- Invalid split ranges leave the source available and unchanged.
- Reviewed split parts retain page lineage, selected types and Original/Copy.
- Attachment makes the documents visible in the LC file and removes them from
  the open inbox, while the original bundle remains available.
- Raster previews before and after attachment return PNG, bounded dimensions,
  no-store headers and correct page counts; out-of-range pages are rejected.
- Learned patterns do not inherit original/copy designations from presentations.
- A 42-page digital bundle is pretrained, split into three 14-page documents,
  attached as numbered originals/copy and previewed on its final child pages.
- Numbered originals 1–3 survive batch upload and editable metadata persistence.

All PDFs are generated from synthetic text in the test runner. These are real
HTTP/database/rendering checks, not browser usability tests or proof that a
large scanned production PDF finishes OCR within its configured budget.
Visual layout, actual scan quality, stamp readability and OCR throughput still
require representative private-document tests.
