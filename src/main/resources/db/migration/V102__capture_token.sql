-- Machine access for scan stations: a token per tenant that may only deliver documents to the inbox. Only the hash of the token is stored.
CREATE TABLE capture_token (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 name varchar(100) NOT NULL,
 token_hash varchar(64) NOT NULL,
 token_prefix varchar(12) NOT NULL,
 created_by varchar(100),
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_used_at timestamp,
 revoked_at timestamp,
 CONSTRAINT capture_token_hash_unique UNIQUE (token_hash)
);
CREATE INDEX idx_capture_token_tenant ON capture_token (tenant_id);
