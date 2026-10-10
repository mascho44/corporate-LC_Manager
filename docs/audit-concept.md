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

## Step 4: read access, search, export, retention

* **Read access is recorded:** `DOCUMENT_VIEWED` / `DOCUMENT_DOWNLOADED` (stored documents), `DOCUMENT_INBOX_VIEWED`,
  `AUDIT_VIEWED`, `AUDIT_EXPORTED`, `AUDIT_CHAIN_VERIFIED`; dossier export and check report were already recorded.
  Page thumbnails are not recorded individually.
* **Search:** `GET /api/audit?from=&to=&user=&action=&entityId=&limit=` (period inclusive, user and action as
  case-insensitive "contains" with literal wildcards, up to 2000 hits; without filters the 200 most recent events).
  The audit dialog offers period, user and "Zeitraum laden".
* **Export:** `GET /api/audit/export.csv` with the same filters, up to 50,000 rows including chain data.
* **Retention:** the guideline is `lc.audit.retention-years` (environment `AUDIT_RETENTION_YEARS`, default 10,
  range 1-100). `GET /api/audit/retention` and the audit dialog show the period, the oldest entry and how many
  entries are older than the period. **Nothing is deleted automatically.** Deleting entries after the period is a
  deliberate, documented operation (it needs a chain checkpoint so verification stays possible; not implemented).
  Please confirm the period with your compliance function (BAIT/DORA do not name a single number for all log types).

## Step 5: external anchor of the chain head
The database verifies that no entry was changed or removed, but whoever controls the database could rewrite the whole chain. `scripts/audit-anchor.sh`
stores the head (sequence number and hash) of every tenant chain **outside** the database, so a rewrite or rollback becomes visible.

* Each run asks `verify_audit_chain` for all tenants and appends one line per tenant to `$HOME/audit-anchors/anchors.log` (mode 600):
  `time|tenant|OK/BAD|checked|head sequence|head hash|unchained|SHA-256 of the previous line`. The last field chains the file itself.
* Alarm (exit code 1, message on stderr, line marked `BAD`): the chain is broken, the head sequence went **back**, the hash at an already anchored sequence
  **changed**, or the anchor file was edited or shortened in the middle (checked before every run; `scripts/audit-anchor.sh --check-log` checks the file only).
  Technical errors (database unreachable) exit with 2 and leave the file unchanged.
* With `RESTIC_REPOSITORY` and `RESTIC_PASSWORD_FILE` set (same setup as the off-site backup) the file is also saved encrypted to the external repository,
  which protects against truncation at the end and against loss of the server.
* Run hourly via cron on the server, for example:
  `0 * * * * cd /home/administrator/corporate-lc-manager && ./scripts/audit-anchor.sh >> $HOME/audit-anchors/cron.log 2>&1`
  and watch the exit code (the server monitoring mail can be pointed at `cron.log` or at the exit status).
* Limits: the anchor proves the chain was not rewritten **between** two anchors; entries made and rewritten within one interval are not covered, and
  a person with access to both the database and the anchor store defeats it. Keep the external copy under separate access rights.
