-- Customer master data (first source: entered by hand; `source` and `external_ref` leave room for connectors to other systems) and the customer chosen for an inbox document.
CREATE TABLE customer (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 name varchar(255) NOT NULL,
 customer_number varchar(60),
 account_number varchar(60),
 iban varchar(34),
 bic varchar(11),
 source varchar(30) NOT NULL DEFAULT 'MANUAL',
 external_ref varchar(100),
 created_by varchar(100),
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX customer_number_unique ON customer (tenant_id, customer_number) WHERE customer_number IS NOT NULL;
CREATE UNIQUE INDEX customer_external_unique ON customer (tenant_id, source, external_ref) WHERE external_ref IS NOT NULL;
CREATE INDEX idx_customer_iban ON customer (tenant_id, iban);
CREATE TABLE inbox_customer_assignment (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 inbox_item_id uuid NOT NULL REFERENCES document_inbox_item(id) ON DELETE CASCADE,
 customer_id uuid NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
 method varchar(12) NOT NULL,
 assigned_by varchar(100),
 assigned_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT inbox_customer_assignment_one UNIQUE (inbox_item_id)
);
