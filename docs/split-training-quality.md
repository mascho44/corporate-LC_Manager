# Split training quality

The Training & Recognition page exposes workspace-local correction measurements at `GET /api/training/document-types/quality` (authenticated, tenant scoped, no-store).

From migration V77 onward confirmations store the proposed and confirmed page ranges/types. No PDF bytes or recognized document text are added to the training table. Original/copy designations are excluded from both snapshots.

Pretraining compares against the signed preview actually returned to the reviewer. Inbox splits compare against the recommendation recomputed within the confirmation transaction; the method is marked `CONFIRM_TIME_` and the UI explicitly identifies this timing. Concurrent training may therefore make this recommendation different from an earlier displayed proposal.

The report covers at most the latest 1,000 measured confirmations, with 20 recent metadata summaries. It counts reviewed pages, corrected page types and confirmations with changed boundaries independently. Historical confirmations without a proposal remain unmeasured. This is observed reviewer feedback, not a statistical confidence estimate, general ML learning, or proof of financial correctness. Exact normalized pattern replay still requires human review; conflicting patterns do not become majority decisions.

Training remains tenant-local. The existing tenant deletion policy removes these metadata rows with the tenant. Signed receipts expire after one hour and are invalidated by application restart.
