# Tenant archive and deletion concept

Status: evolving implementation specification, 2026-10-08. Inventory and reversible
archive metadata are implemented; see [Tenant lifecycle](tenant-lifecycle.md).
An opt-in endpoint for **disposable test data only** now exists; see
[Test-data purge](tenant-test-purge.md). It is disabled by default and is not the
production retention/approval workflow specified below. Retention periods and
approval policy require owner approval before irreversible deletion is enabled.
This is a technical design, not a statement of statutory retention obligations.

## Implemented first step: read-only inventory

Platform administration provides an inline **Inventory / deletion preview** button
per tenant and `GET /api/platform/tenants/{id}/inventory`. It requires live global
platform authorization, uses explicit tenant-qualified aggregates and a read-only
repeatable-read database snapshot. The caller's selected workspace is unchanged.
The response contains category counts and original binary-content byte totals,
not document content, account identities, bearer tokens or learning examples.

Byte totals cover document, inbox, template and training binary columns only;
they are not database/storage usage. Draft approvals are contained in draft rows,
and retained learning/reviews are contained in training sessions. Counts are not
a promise that every row is immediately deletable. Shared-membership counts use
other membership records irrespective of whether those accesses are suspended.
Global accounts, credentials, avatars and reset tokens remain excluded.

Known work includes queued/processing inbox extraction, pending/dead-letter
integration messages and persisted invitation mail payloads. This is not an
in-flight request barrier. Retention policy, holds, backup copies and filesystem
temporary artifacts are explicitly reported as unresolved. `deletionAllowed`
always remains false, including for an empty tenant. No purge action is exposed.
Archive/restore is a separate reversible lifecycle action. Inventory is available for suspended tenants to authorized platform
administrators, without reopening ordinary business access.

Verification (2026-10-08): 551 backend cases, no failures/errors, two skipped;
112 frontend tests passed. Disposable PostgreSQL acceptance verified isolated
category counts/binary lengths, shared membership indication, current/default
protection, denied non-platform access, unchanged workspace selection, missing
tenant response and inventory access while suspended. Synthetic containers and
database were removed. No production changes or visual browser-layout test.

## Existing behavior

Tenant suspension is reversible and retains business data and credentials.
The default and currently selected tenants cannot be suspended. Existing workers
skip suspended tenants, but a running request/job is not forcibly interrupted.
Suspension is therefore not a deletion barrier or an archive workflow.

User identities, credentials, avatars and password-reset tokens are global.
Memberships and business data are tenant-specific. Removing one tenant must never
delete a global account or affect its other memberships. Invitation payloads are
global records bound to a target tenant and must also be considered.

## Lifecycle

Active → Suspended → Archived → Deletion requested → Approved → Purging → Purged.

These are proposed states, not currently available API values. Suspension and
archival remain reversible until purge begins. Archived tenants cannot accept new
work, invitations or jobs. Read-only archive/export access requires a separate,
explicit platform permission and tenant-specific audit; ordinary business access
stays blocked. Reactivation restores neither revoked accounts nor memberships.
Default/current workspace protection applies to archive and purge too.

## Retention policy

Each tenant policy identifies data category, retention trigger, duration,
effective version and approver. Unconfigured categories are not automatically
deleted. A documented legal/business hold blocks the affected category and full
tenant purge; releasing a hold is a separately audited action.

Distinguish deletion of training source documents from deletion of learned data.
Source-only deletion may retain confirmed learning within the same tenant; full
tenant purge must remove both. Learning must not silently migrate to other tenants.

## Inventory and preview

The preview must be read-only, explicitly scoped by tenant ID and report counts,
byte totals where available, holds, pending jobs and policy version. It must cover:

- LC records, conditions, required documents, amendments, tasks and notes;
- original/generated documents, drafts, approvals, comparisons and check decisions;
- document inbox, OCR/extracted facts, import history and derived artifacts;
- training originals, sessions, confirmed learning and learning controls;
- company profiles, company templates and approval thresholds;
- tenant roles, role permissions, memberships and suspensions;
- target-bound invitations and their encrypted queued mail payloads;
- outbox messages, delivery records, cached exports and temporary job files;
- audit records and backup copies, handled under separate approved policies.

Current document, inbox, template and training originals include database bytea
content. Inventory must additionally inspect actual filesystem/job storage and
learning persistence before implementation; entity names alone are not a complete
storage map. Full purge must fail closed on an unknown storage category.

## Request and approval

A platform administrator with verified TOTP requests deletion from another
workspace. Require tenant-code confirmation, purpose and a current preview.
No DELETE action is offered through a local tenant administrator role.

Irreversible purge requires approval by a distinct authorized global identity
with verified TOTP. Approval binds tenant, policy version and inventory revision;
changes invalidate it. Authorization and holds are rechecked when execution begins.
Four-eyes approval remains a prerequisite even though its wider business workflow
is a separate roadmap item. No implicit test-mode bypass applies to production.

Before approving, show that export is optional and itself creates a sensitive copy.
Exports have their own access control, expiry and cleanup. Do not automatically
create an indefinite full-data archive as part of a deletion request.

## Execution and concurrent work

Install a persistent write/job barrier before the final inventory. Serialize
tenant administration in the established home-lock/target-lock order. Drain or
cancel in-flight jobs and stop scheduled deliveries before deleting their inputs.
Every mutating service and worker must enforce the barrier; a UI flag is insufficient.

Use a durable, resumable deletion job with bounded batches, idempotent steps and
tenant-qualified predicates. Delete dependent objects before parents. Do not use
unscoped bulk repository deletion or bypass append-only audit safeguards.
Failure leaves the tenant blocked and the job retryable, with a safe error category.
Recheck remaining counts and derived/file storage before marking purge complete.

Keep a minimal platform tombstone/job receipt independent of deleted tenant foreign
keys: opaque tenant ID, request/approval/execution timestamps, policy version,
category counts, outcomes and authorized actor identifiers under the audit policy.
Exclude document text, credentials, bearer links and unnecessary personal data.
Tenant audit retention or pseudonymization needs an explicit policy and controlled
maintenance path, not deletion through ordinary append-only repositories.

## Backups and restore

Deleting live rows does not immediately delete backup copies. The policy must
define backup retention and the last scheduled expiry of affected copies. Retained
backups remain access-controlled; do not claim complete erasure until that period
ends or a separately approved backup-erasure procedure has completed.

Maintain a deletion ledger outside the restored backup's lifecycle. Restore into
an isolated environment, reapply completed deletion barriers/purges from that
ledger and verify them before enabling user access or outbound jobs/mail.
Until this procedure is tested, backups are an explicit residual-data limitation.

## Implementation sequence and acceptance gates

1. Build tenant-scoped inventory/preview, including learning and non-entity storage.
2. Implement archival, retention policies and holds with audited permissions.
3. Add two-person deletion requests, approvals and a complete write/job barrier.
4. Implement resumable purge and minimal receipt; test interruption and retries.
5. Test backup expiry and isolated restore with deletion-ledger reapplication.

Acceptance uses two synthetic tenants sharing a global user. Prove no cross-tenant
loss, no deletion of the shared user, source-only learning preservation, complete
full-purge learning removal, hold enforcement, stale-approval rejection, denied
self-approval/default/current deletion, atomic audit rollback, concurrent-job
handling and restore protection. No production purge until all gates pass.
