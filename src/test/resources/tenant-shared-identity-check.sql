-- Disposable PostgreSQL ONLY after migrations through V62. All changes roll back.
BEGIN;
ALTER TABLE tenant DROP CONSTRAINT tenant_bootstrap_single;
INSERT INTO tenant(id,code,name) VALUES ('10000000-0000-0000-0000-000000000002','synthetic-shared','Synthetic second tenant');
INSERT INTO app_role(id,name,base_role,tenant_id) VALUES ('40000000-0000-0000-0000-000000000001','Synthetic second role','VIEWER','10000000-0000-0000-0000-000000000002');
INSERT INTO app_user(id,username,display_name,password_hash,role,created_at,assigned_role_id)
 VALUES ('50000000-0000-0000-0000-000000000001','synthetic-shared-identity','Synthetic identity','synthetic-noncredential-hash','ADMIN',now(),'00000000-0000-0000-0000-000000000001');
-- No application provisioning path is enabled. Only the disposable fixture bypasses the guard.
ALTER TABLE tenant_membership DISABLE TRIGGER tenant_membership_write_guard;
INSERT INTO tenant_membership(tenant_id,user_id,role_id,active)
 VALUES ('10000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001',true);
ALTER TABLE tenant_membership ENABLE TRIGGER tenant_membership_write_guard;
UPDATE app_user SET assigned_role_id='00000000-0000-0000-0000-000000000002',active=false WHERE id='50000000-0000-0000-0000-000000000001';
DO $$ BEGIN
 IF (SELECT count(*) FROM tenant_membership WHERE user_id='50000000-0000-0000-0000-000000000001')<>2 THEN
  RAISE EXCEPTION 'Identity did not retain both memberships';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM tenant_membership WHERE user_id='50000000-0000-0000-0000-000000000001' AND tenant_id='10000000-0000-0000-0000-000000000002' AND role_id='40000000-0000-0000-0000-000000000001' AND active=true) THEN
  RAISE EXCEPTION 'Home administration overwrote a foreign membership';
 END IF;
END $$;
DELETE FROM app_user WHERE id='50000000-0000-0000-0000-000000000001';
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM tenant_membership WHERE user_id='50000000-0000-0000-0000-000000000001') THEN
  RAISE EXCEPTION 'Deleted identity retained memberships';
 END IF;
END $$;
ROLLBACK;
