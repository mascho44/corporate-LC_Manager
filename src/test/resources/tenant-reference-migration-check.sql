-- Run ONLY against a disposable database after migrations V1 through V61.
BEGIN;
DO $$ BEGIN
 BEGIN
  INSERT INTO tenant(id,code,name) VALUES ('10000000-0000-0000-0000-000000000002','synthetic-foreign','Synthetic foreign');
  RAISE EXCEPTION 'Bootstrap gate unexpectedly allows a second tenant';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
END $$;
-- Temporarily relax the gate inside this rolled-back test transaction only.
ALTER TABLE tenant DROP CONSTRAINT tenant_bootstrap_single;
INSERT INTO tenant(id,code,name) VALUES ('10000000-0000-0000-0000-000000000002','synthetic-foreign','Synthetic foreign');
INSERT INTO letter_of_credit(id,reference) VALUES ('20000000-0000-0000-0000-000000000001','SYNTHETIC-SHARED');
INSERT INTO letter_of_credit(id,reference,tenant_id) VALUES ('20000000-0000-0000-0000-000000000002','SYNTHETIC-SHARED','10000000-0000-0000-0000-000000000002');
DO $$ BEGIN
 BEGIN
  INSERT INTO letter_of_credit(id,reference) VALUES ('20000000-0000-0000-0000-000000000003','SYNTHETIC-SHARED');
  RAISE EXCEPTION 'Duplicate reference was not rejected';
 EXCEPTION WHEN unique_violation THEN NULL;
 END;
END $$;
ROLLBACK;
