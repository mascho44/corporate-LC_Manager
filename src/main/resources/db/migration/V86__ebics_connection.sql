-- EBICS bank connection per tenant. Key material is stored only AES-GCM encrypted (see EbicsCipher).
CREATE TABLE ebics_connection (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 url varchar(500) NOT NULL,
 host_id varchar(35) NOT NULL,
 partner_id varchar(35) NOT NULL,
 user_id varchar(35) NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'NEW',
 bank_blob bytea,
 partner_blob bytea,
 user_blob bytea,
 last_error varchar(500),
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ebics_connection_one_per_tenant UNIQUE (tenant_id),
 CONSTRAINT ebics_connection_status CHECK (status IN ('NEW','KEYS_SENT','ACTIVE','ERROR'))
);
