-- Trade-finance messages (MT7xx) fetched from the bank via EBICS. The same message is stored once per tenant.
CREATE TABLE ebics_message (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 message_type varchar(10) NOT NULL,
 sha256 varchar(64) NOT NULL,
 content text NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'NEW',
 note varchar(500),
 received_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 handled_at timestamp,
 handled_by varchar(100),
 CONSTRAINT ebics_message_unique UNIQUE (tenant_id, sha256),
 CONSTRAINT ebics_message_status CHECK (status IN ('NEW','IMPORTED','DISCARDED')),
 CONSTRAINT ebics_message_type CHECK (message_type IN ('MT700','MT707','MT710','MT760'))
);
CREATE INDEX ebics_message_tenant_received ON ebics_message(tenant_id, received_at DESC);
