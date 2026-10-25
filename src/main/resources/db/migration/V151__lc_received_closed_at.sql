-- When a file came in and when it was finished (closed or expired), for the trend charts. Existing files: the first audit event of the file
-- (else the issue date, else now) and, for finished files, the expiry date (else now).
ALTER TABLE letter_of_credit ADD COLUMN received_at timestamp;
ALTER TABLE letter_of_credit ADD COLUMN closed_at timestamp;
UPDATE letter_of_credit lc SET received_at = COALESCE(
  (SELECT min(a.occurred_at) FROM audit_event a WHERE a.entity_id = lc.id::text AND a.tenant_id = lc.tenant_id),
  lc.issue_date::timestamp, now());
UPDATE letter_of_credit SET closed_at = COALESCE(expiry_date::timestamp, now()) WHERE status IN ('CLOSED','EXPIRED');
ALTER TABLE letter_of_credit ALTER COLUMN received_at SET NOT NULL;
CREATE INDEX idx_letter_of_credit_received ON letter_of_credit(tenant_id, received_at);
