DO $$
DECLARE table_name text;
BEGIN
 FOR table_name IN SELECT unnest(ARRAY['document_comparison', 'email_delivery']) LOOP
  EXECUTE format('ALTER TABLE %I ADD COLUMN tenant_id uuid', table_name);
  EXECUTE format('UPDATE %I child SET tenant_id=lc.tenant_id FROM letter_of_credit lc WHERE child.lc_id=lc.id', table_name);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', table_name);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT %L::uuid', table_name, '00000000-0000-0000-0000-000000000001');
  EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (tenant_id) REFERENCES tenant(id)', table_name, table_name || '_tenant_fk');
  EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (tenant_id, lc_id) REFERENCES letter_of_credit(tenant_id, id) ON DELETE CASCADE', table_name, table_name || '_lc_same_tenant');
  EXECUTE format('CREATE INDEX %I ON %I(tenant_id, lc_id)', table_name || '_tenant_lc', table_name);
 END LOOP;
END $$;
