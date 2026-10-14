DO $$
DECLARE child record;
BEGIN
 FOR child IN SELECT * FROM (VALUES
  ('lc_note','letter_of_credit_id'),('lc_task','letter_of_credit_id'),
  ('lc_amendment','lc_id'),('document_check_decision','lc_id'),
  ('lc_requirement_mapping','letter_of_credit_id')
 ) AS children(table_name,lc_column)
 LOOP
  EXECUTE format('ALTER TABLE %I ADD COLUMN tenant_id uuid',child.table_name);
  EXECUTE format('UPDATE %I c SET tenant_id=lc.tenant_id FROM letter_of_credit lc WHERE c.%I=lc.id',child.table_name,child.lc_column);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL',child.table_name);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT %L',child.table_name,'00000000-0000-0000-0000-000000000001');
  EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY(tenant_id) REFERENCES tenant(id)',child.table_name,child.table_name||'_tenant_fk');
  EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY(tenant_id,%I) REFERENCES letter_of_credit(tenant_id,id) ON DELETE CASCADE',child.table_name,child.table_name||'_lc_same_tenant',child.lc_column);
  EXECUTE format('CREATE INDEX %I ON %I(tenant_id,%I)',child.table_name||'_tenant_lc_idx',child.table_name,child.lc_column);
 END LOOP;
END $$;
