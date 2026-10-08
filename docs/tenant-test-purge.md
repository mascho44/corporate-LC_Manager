# Explicit deletion of disposable tenant data

Disabled by default. `lc.tenants.test-purge-enabled=true` enables a platform-only
DELETE `/api/platform/tenants/{id}` for disposable test installations. This is not
the retention/hold/two-person production workflow in tenant-retention-and-deletion.md.
Do not enable it for real business records. No production purge is run by tests.

The tenant must be suspended and archived, not the default or selected tenant.
Require live global platform authorization with verified TOTP, CSRF and the exact
tenant code plus acknowledgements of full business-data deletion and retained
audit/backups. Queued/processing extraction blocks deletion.

V73 installs PostgreSQL tenant write barriers: writes take a shared tenant-row lock;
purge takes the administration row lock, waits for writes, and marks an irreversible
inactive tombstone. Further tenant-owned inserts/updates are denied. Cleanup runs
inside the same transaction and fails if unknown tenant-owned categories remain.
FK errors or audit receipt failures roll back the tombstone and all cleanup.

Documents, drafts, LCs and child rows, training (including learning), company data,
templates, rule packs, charges, imports, invitations/encrypted mail, outbox, roles
and memberships are deleted. Global identities and their other memberships remain.
Append-only audit may contain historical document-derived or personal information;
it is NOT erased. Backups and transient files held by already-running OCR are NOT
erased by this operation. In-flight external messages cannot be unsent. A minimal
tenant tombstone remains to preserve audit attribution and block late writes; its
code cannot be reused. This is live business-data deletion, not complete erasure.
