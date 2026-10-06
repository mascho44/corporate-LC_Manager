ALTER TABLE internal_rule_pack_version ADD COLUMN tenant_id uuid NOT NULL
 DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES tenant(id);
ALTER TABLE internal_rule_pack_version DROP CONSTRAINT internal_rule_pack_version_pack_id_pack_version_key;
ALTER TABLE internal_rule_pack_version ADD CONSTRAINT rule_pack_tenant_version_unique UNIQUE(tenant_id,pack_id,pack_version);
ALTER TABLE internal_rule_pack_version ADD CONSTRAINT rule_pack_tenant_identity_unique UNIQUE(tenant_id,pack_id,id);
ALTER TABLE internal_rule_pack DROP CONSTRAINT internal_rule_pack_pkey;
ALTER TABLE internal_rule_pack ADD PRIMARY KEY(tenant_id,id);
ALTER TABLE internal_rule_pack ADD CONSTRAINT rule_pack_active_same_tenant
 FOREIGN KEY(tenant_id,id,active_version_id) REFERENCES internal_rule_pack_version(tenant_id,pack_id,id);
ALTER TABLE internal_rule_pack ADD CONSTRAINT rule_pack_previous_same_tenant
 FOREIGN KEY(tenant_id,id,previous_version_id) REFERENCES internal_rule_pack_version(tenant_id,pack_id,id);
CREATE INDEX rule_pack_tenant_imported_idx ON internal_rule_pack_version(tenant_id,imported_at DESC);
