-- An MT707 amendment takes effect only after it was accepted. Amendments imported so far were applied immediately: ACCEPTED.
ALTER TABLE lc_amendment
 ADD COLUMN status varchar(12) NOT NULL DEFAULT 'ACCEPTED',
 ADD COLUMN decided_at timestamp,
 ADD COLUMN decided_by varchar(100),
 ADD COLUMN decision_comment varchar(500),
 ADD COLUMN other_changes text,
 ADD CONSTRAINT lc_amendment_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED'));
UPDATE lc_amendment SET decided_at = imported_at, decided_by = 'Übernahme beim Import' WHERE status = 'ACCEPTED';
ALTER TABLE lc_amendment ALTER COLUMN status SET DEFAULT 'PENDING';
