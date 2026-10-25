-- One daily procurement digest per tenant: the row is written before sending, so a restart never sends a second one for the same day.
CREATE TABLE procurement_digest_log (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 sent_on date NOT NULL,
 recipients integer NOT NULL DEFAULT 0,
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT procurement_digest_log_one UNIQUE (tenant_id, sent_on)
);
