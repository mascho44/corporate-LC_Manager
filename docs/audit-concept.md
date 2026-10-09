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
