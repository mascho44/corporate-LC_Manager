-- Richer audit context (BAIT/DORA): UTC timestamp with zone, actor roles, hashed session reference,
-- request id for correlation, user agent and an explicit failure reason. Older rows keep NULL here;
-- the append-only triggers stay untouched.
ALTER TABLE audit_event ADD COLUMN occurred_at_utc timestamptz;
ALTER TABLE audit_event ADD COLUMN actor_roles varchar(120);
ALTER TABLE audit_event ADD COLUMN session_ref varchar(16);
ALTER TABLE audit_event ADD COLUMN request_id varchar(40);
ALTER TABLE audit_event ADD COLUMN user_agent varchar(300);
ALTER TABLE audit_event ADD COLUMN failure_reason varchar(500);
