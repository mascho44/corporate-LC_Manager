ALTER TABLE charge_profile ADD CONSTRAINT charge_profile_tenant_id_unique UNIQUE(tenant_id,id);
ALTER TABLE charge_estimate ADD COLUMN tenant_id uuid;
UPDATE charge_estimate e SET tenant_id=lc.tenant_id FROM letter_of_credit lc WHERE lc.id=e.lc_id;
ALTER TABLE charge_estimate ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE charge_estimate ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE charge_estimate ADD CONSTRAINT charge_estimate_tenant_fk FOREIGN KEY(tenant_id) REFERENCES tenant(id);
ALTER TABLE charge_estimate ADD CONSTRAINT charge_estimate_lc_same_tenant
 FOREIGN KEY(tenant_id,lc_id) REFERENCES letter_of_credit(tenant_id,id) ON DELETE CASCADE;
ALTER TABLE charge_estimate ADD CONSTRAINT charge_estimate_profile_same_tenant
 FOREIGN KEY(tenant_id,profile_id) REFERENCES charge_profile(tenant_id,id);
CREATE INDEX charge_estimate_tenant_lc_created_idx ON charge_estimate(tenant_id,lc_id,created_at DESC);
CREATE INDEX swift_import_tenant_imported_idx ON swift_import_record(tenant_id,imported_at DESC);
