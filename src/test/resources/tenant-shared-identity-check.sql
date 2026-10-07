-- Disposable PostgreSQL ONLY after migrations through V64. All changes roll back.
BEGIN;
ALTER TABLE tenant DROP CONSTRAINT tenant_bootstrap_single;
INSERT INTO tenant(id,code,name) VALUES ('10000000-0000-0000-0000-000000000002','synthetic-shared','Synthetic second tenant');
INSERT INTO app_role(id,name,base_role,tenant_id) VALUES ('40000000-0000-0000-0000-000000000001','Synthetic second role','VIEWER','10000000-0000-0000-0000-000000000002');
INSERT INTO app_user(id,username,display_name,password_hash,role,created_at,assigned_role_id)
 VALUES ('50000000-0000-0000-0000-000000000001','synthetic-shared-identity','Synthetic identity','synthetic-noncredential-hash','ADMIN',now(),'00000000-0000-0000-0000-000000000001');
-- Owner-only provisioning without disabling the membership write guard.
SELECT provision_tenant_membership('10000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001');
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM tenant_membership_provision_command) THEN
  RAISE EXCEPTION 'Provisioning retained a transient command';
 END IF;
 BEGIN
  PERFORM provision_tenant_membership('10000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001');
  RAISE EXCEPTION 'Duplicate membership was accepted';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
 BEGIN
  PERFORM provision_tenant_membership('10000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001');
  RAISE EXCEPTION 'Foreign role was accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
END $$;
DO $$ DECLARE rejected boolean=false; BEGIN
 BEGIN
  PERFORM provision_tenant_membership('00000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001');
 EXCEPTION WHEN raise_exception THEN rejected=true;
 END;
 IF NOT rejected THEN RAISE EXCEPTION 'Home membership provisioning was accepted'; END IF;
 IF EXISTS (SELECT 1 FROM pg_proc p, LATERAL aclexplode(p.proacl) a
  WHERE p.oid='provision_tenant_membership(uuid,uuid,uuid)'::regprocedure
  AND a.grantee=0 AND a.privilege_type='EXECUTE') THEN
  RAISE EXCEPTION 'Public provisioning execution is enabled';
 END IF;
END $$;
INSERT INTO tenant_membership_suspension(tenant_id,user_id,suspended)
 VALUES ('00000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001',true),
 ('10000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001',false);
-- The role-only administration bridge must preserve global identity/activation.
UPDATE app_user SET assigned_role_id='00000000-0000-0000-0000-000000000002',role='EDITOR'
 WHERE id='50000000-0000-0000-0000-000000000001';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM tenant_membership WHERE user_id='50000000-0000-0000-0000-000000000001' AND tenant_id='00000000-0000-0000-0000-000000000001' AND role_id='00000000-0000-0000-0000-000000000002' AND active=true) THEN
  RAISE EXCEPTION 'Role-only home update did not sync the active membership';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM app_user WHERE id='50000000-0000-0000-0000-000000000001' AND username='synthetic-shared-identity' AND display_name='Synthetic identity' AND password_hash='synthetic-noncredential-hash' AND active=true) THEN
  RAISE EXCEPTION 'Role-only update changed global identity fields';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM tenant_membership_suspension WHERE user_id='50000000-0000-0000-0000-000000000001' AND tenant_id='00000000-0000-0000-0000-000000000001' AND suspended=true) THEN
  RAISE EXCEPTION 'Role-only update cleared the tenant suspension';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM tenant_membership_suspension WHERE user_id='50000000-0000-0000-0000-000000000001' AND tenant_id='10000000-0000-0000-0000-000000000002' AND suspended=false) THEN
  RAISE EXCEPTION 'Home suspension changed foreign access state';
 END IF;
END $$;
UPDATE tenant_membership_suspension SET suspended=false
 WHERE user_id='50000000-0000-0000-0000-000000000001' AND tenant_id='00000000-0000-0000-0000-000000000001';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM app_user WHERE id='50000000-0000-0000-0000-000000000001' AND active=true AND password_hash='synthetic-noncredential-hash') THEN
  RAISE EXCEPTION 'Suspension update changed global credentials or activation';
 END IF;
 BEGIN
  INSERT INTO tenant_membership_suspension(tenant_id,user_id,suspended)
   VALUES ('10000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000099',true);
  RAISE EXCEPTION 'Restriction without a membership was accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
END $$;
DO $$ DECLARE rejected boolean=false; BEGIN
 BEGIN
  UPDATE tenant_membership SET active=false WHERE user_id='50000000-0000-0000-0000-000000000001';
 EXCEPTION WHEN raise_exception THEN rejected=true;
 END;
 IF NOT rejected THEN RAISE EXCEPTION 'Direct membership write guard was weakened'; END IF;
END $$;
UPDATE app_user SET assigned_role_id='00000000-0000-0000-0000-000000000002',active=false WHERE id='50000000-0000-0000-0000-000000000001';
DO $$ DECLARE rejected boolean=false; BEGIN
 BEGIN
  PERFORM provision_tenant_membership('10000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001');
 EXCEPTION WHEN raise_exception THEN rejected=true;
 END;
 IF NOT rejected THEN RAISE EXCEPTION 'Inactive identity was accepted'; END IF;
 IF EXISTS (SELECT 1 FROM tenant_membership_provision_command) THEN
  RAISE EXCEPTION 'Rejected provisioning retained a command';
 END IF;
END $$;
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
 IF EXISTS (SELECT 1 FROM tenant_membership_suspension WHERE user_id='50000000-0000-0000-0000-000000000001') THEN
  RAISE EXCEPTION 'Deleted membership retained access restrictions';
 END IF;
END $$;
ROLLBACK;
