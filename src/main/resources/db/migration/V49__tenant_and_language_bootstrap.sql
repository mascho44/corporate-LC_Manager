-- Fail-closed bootstrap: no second tenant until access isolation is implemented.
CREATE TABLE tenant (
 id uuid PRIMARY KEY, code varchar(100) NOT NULL UNIQUE, name varchar(255) NOT NULL,
 default_language varchar(20) NOT NULL DEFAULT 'en',
 bank_enabled boolean NOT NULL DEFAULT true, corporate_enabled boolean NOT NULL DEFAULT false,
 CONSTRAINT tenant_bootstrap_single CHECK (id='00000000-0000-0000-0000-000000000001')
);
INSERT INTO tenant(id,code,name) VALUES ('00000000-0000-0000-0000-000000000001','default','Default tenant');
ALTER TABLE app_user ADD COLUMN tenant_id uuid NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES tenant(id);
ALTER TABLE app_user ADD COLUMN preferred_language varchar(20);
-- Attribute root records, but do not claim existing repositories are tenant-filtered.
DO $$
DECLARE table_name text;
BEGIN
 FOREACH table_name IN ARRAY ARRAY['letter_of_credit','document_inbox_item','training_session',
 'training_learning_control','app_role','company_profile','document_template','swift_import_record',
 'charge_profile','audit_event','internal_rule_pack','integration_outbox']
 LOOP
  EXECUTE format('ALTER TABLE %I ADD COLUMN tenant_id uuid NOT NULL DEFAULT %L REFERENCES tenant(id)',table_name,'00000000-0000-0000-0000-000000000001');
  EXECUTE format('CREATE INDEX %I ON %I(tenant_id)',table_name || '_tenant_idx',table_name);
 END LOOP;
END $$;
