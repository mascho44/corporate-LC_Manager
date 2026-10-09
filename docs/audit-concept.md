# Audit log: context per event (step 1 of the BAIT/DORA programme)

Every audit event now records, in addition to user, action, object, details and outcome:

| Field | Content | Notes |
|---|---|---|
| `occurred_at_utc` | UTC instant with zone (`timestamptz`) | Rows before V84 have NULL here; their local `occurred_at` remains. |
| `actor_roles` | base roles of the acting identity (`ADMIN`, `USER`, ...) | NULL outside an authenticated request. |
| `session_ref` | first 16 hex characters of SHA-256 of the session id | The session id itself is never stored. |
| `request_id` | server-generated id (`req-...`), also returned as `X-Request-Id` and put in the log MDC (`requestId`) | Client-supplied ids are ignored. |
| `user_agent` | browser header, control characters removed, max. 300 characters | |
| `ip_address` | address the trusted reverse proxy saw (right-most `X-Forwarded-For` entry), else the connection address | Earlier `X-Forwarded-For` entries are client-supplied and not trusted. Valid only with exactly one trusted proxy in front. |
| `failure_reason` | for unsuccessful events the reason | |

Background jobs and events outside a web request keep these fields empty. The CSV export contains the
new columns after the previous ones. The append-only triggers (no UPDATE, DELETE, TRUNCATE) are unchanged.

Next steps: structured before/after for all changing actions, hash chain with verification, read access
logging, filters, full export and retention (see the programme in the project notes).

## Step 2: change evidence (before/after)

Changing actions now write structured JSON into the before/after columns (allowlisted fields only,
never file contents, extracted text or free-form notes; always valid JSON within the 4000-character column):

| Action | Before | After |
|---|---|---|
| `LC_DELETED` | LC master data, document count and up to 20 documents (filename, type, short hash) | – |
| `DOCUMENT_UPLOADED`, `DOCUMENT_INBOX_ATTACHED` | – | document (id, filename, type, copy, date, amount, currency, size) |
| `DOCUMENT_DELETED` | document incl. SHA-256 of the content | – |
| `DOCUMENT_INBOX_DELETED` | inbox item incl. SHA-256 | – |
| `LC_ASSIGNED` | previous assignee | new assignee |
| `LC_UPDATED`, `DOCUMENT_UPDATED`, role/membership actions | (unchanged, already structured) | |

`TRAINING_PROGRESS_SAVED` (every autosave, about a quarter of all events) is no longer written to the audit log;
`TRAINING_CONFIRMED` remains.

## Step 3: tamper evidence (hash chain)

The database itself chains the audit rows of each tenant (trigger `trg_audit_event_chain`, migration V85):
each new row gets `chain_seq` (1, 2, 3 ...), `prev_hash` (hash of the previous row, 64 zeros for the first one)
and `entry_hash` = SHA-256 over `prev_hash` and all audit fields of the row (`audit_event_hash`, timestamps
rendered independent of the session time zone). Writers of the same tenant are serialized with an advisory lock
until their transaction ends, so the chain has no forks or gaps.

* **Start:** the chain starts with an anchor event `AUDIT_CHAIN_STARTED` per tenant (migration time). Rows written
  before that stay unchained (NULL) and are only counted as "unchained" in the verification.
* **Verification:** `verify_audit_chain(tenant)` walks the chain and reports the first broken position
  (`Inhalt veraendert`, `Verkettung unterbrochen`, `Luecke in der Folge`), the head sequence number and head hash.
  Tenant administrators with audit permission use the button "Kette prüfen" in the audit dialog
  (`GET /api/audit/chain`, the check itself is audited as `AUDIT_CHAIN_VERIFIED`); platform administrators verify all
  tenants in the platform console (`GET /api/platform/audit/chain`).
* **What it proves:** edits and deletions of chained rows (also by someone who disables the append-only trigger as a
  database administrator) break the chain and are located. It does not stop a database administrator who rewrites
  the whole chain from a point onward. **Operational control:** record the head hash regularly outside the system
  (for example in the monthly report or by mail); a later check against it exposes a rewritten tail. The CSV export
  contains `Kettennr`, `Vorgaenger-Hash` and `Hash` for independent re-computation.
* **Performance note:** each audit write takes a tenant-wide advisory lock until its transaction ends; audit events
  written inside business transactions therefore serialize with each other per tenant.
