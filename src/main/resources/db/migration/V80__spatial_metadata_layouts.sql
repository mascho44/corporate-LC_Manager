CREATE TABLE document_spatial_layout (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
 profile varchar(100) NOT NULL, labels_json text NOT NULL,
 confirmed_by varchar(100) NOT NULL, confirmed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX spatial_layout_tenant_profile ON document_spatial_layout(tenant_id,profile);
