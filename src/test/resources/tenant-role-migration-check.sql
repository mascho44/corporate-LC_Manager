-- Disposable PostgreSQL only, after migrations through V62.
BEGIN;
ALTER TABLE tenant DROP CONSTRAINT tenant_bootstrap_single;
INSERT INTO tenant(id,code,name) VALUES ('10000000-0000-0000-0000-000000000002','synthetic-foreign','Synthetic foreign');
INSERT INTO app_role(id,name,base_role) VALUES ('30000000-0000-0000-0000-000000000001','Synthetic shared role','VIEWER');
INSERT INTO app_role(id,name,base_role,tenant_id) VALUES ('30000000-0000-0000-0000-000000000002','Synthetic shared role','EDITOR','10000000-0000-0000-0000-000000000002');
DO $$ BEGIN
 BEGIN
  INSERT INTO app_role(id,name,base_role) VALUES ('30000000-0000-0000-0000-000000000003','Synthetic shared role','VIEWER');
  RAISE EXCEPTION 'Duplicate role name was not rejected';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
 IF NOT EXISTS (SELECT 1 FROM app_role WHERE name='Administrator' AND tenant_id='00000000-0000-0000-0000-000000000001') THEN
  RAISE EXCEPTION 'Existing bootstrap role was not preserved';
 END IF;
END $$;
ROLLBACK;
