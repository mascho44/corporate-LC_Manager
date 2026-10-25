CREATE TABLE document_metadata_training (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
 text_hash varchar(64) NOT NULL,
 confirmed_json text NOT NULL,
 confirmed_by varchar(100) NOT NULL,
 confirmed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX metadata_training_tenant_hash ON document_metadata_training(tenant_id,text_hash);
