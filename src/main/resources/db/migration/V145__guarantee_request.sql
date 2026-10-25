-- Beauftragung einer Garantie bei der Bank: Antrag mit Statusverfolgung; Garantien koennen zusaetzlich den Status CANCELLED (nicht zustande gekommen) haben.
ALTER TABLE guarantee_detail DROP CONSTRAINT guarantee_detail_status;
ALTER TABLE guarantee_detail ADD CONSTRAINT guarantee_detail_status CHECK (status IN ('REQUESTED','ACTIVE','RETURNED','CLAIMED','EXPIRED','CANCELLED'));
CREATE TABLE guarantee_request (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 lc_id uuid NOT NULL UNIQUE REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 status varchar(10) NOT NULL DEFAULT 'DRAFT',
 recipient varchar(500),
 our_reference varchar(100),
 wording text,
 remarks varchar(1000),
 sent_on date,
 bank_reference varchar(100),
 response_due date,
 status_date date,
 status_note varchar(500),
 changed_by varchar(100),
 changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT guarantee_request_status CHECK (status IN ('DRAFT','SENT','QUERY','APPROVED','ISSUED','REJECTED','WITHDRAWN'))
);
ALTER TABLE guarantee_request ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON guarantee_request
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
-- Die Loeschung eines Mandanten raeumt auch Garantieantraege ab (Funktion aus V144 mit guarantee_request in der Liste).
CREATE OR REPLACE FUNCTION purge_deleted_tenant_business_data() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE tab text; remaining bigint;
BEGIN
 IF OLD.deleted_at IS NOT NULL THEN
  IF NEW.deleted_at IS DISTINCT FROM OLD.deleted_at THEN RAISE EXCEPTION 'Deleted tenant cannot be restored'; END IF;
  RETURN NEW;
 END IF;
 IF NEW.deleted_at IS NULL THEN RETURN NEW; END IF;
 -- Fixed owner-qualified order. FK failures roll back the entire purge.
 DELETE FROM document_inbox_item WHERE tenant_id=NEW.id;
 DELETE FROM training_session WHERE tenant_id=NEW.id;
 DELETE FROM training_learning_control WHERE tenant_id=NEW.id;
 DELETE FROM document_template WHERE tenant_id=NEW.id;
 DELETE FROM charge_estimate WHERE tenant_id=NEW.id;
 DELETE FROM letter_of_credit WHERE tenant_id=NEW.id;
 DELETE FROM company_profile WHERE tenant_id=NEW.id;
 -- Tenant-level settings and data added after V73 (no cascade fires because the tenant row itself stays as a tombstone). Dependants first: workflows and tasks refer to teams.
 FOREACH tab IN ARRAY ARRAY['guarantee_request','credit_line','guarantee_detail','lc_payment','api_key','webhook_delivery','webhook_subscription','correspondent_bank','sla_rule','lc_task','swift_message','workflow','automation_rule','team','ebics_message','ebics_connection','tenant_ai_setting','tenant_scan_profile','document_metadata_training','document_spatial_layout'] LOOP
  EXECUTE format('DELETE FROM %I WHERE tenant_id=$1',tab) USING NEW.id;
 END LOOP;
 DELETE FROM internal_rule_pack WHERE tenant_id=NEW.id;
 DELETE FROM internal_rule_pack_version WHERE tenant_id=NEW.id;
 DELETE FROM platform_invitation WHERE tenant_id=NEW.id;
 DELETE FROM tenant_membership_role_command WHERE tenant_id=NEW.id;
 DELETE FROM tenant_membership_provision_command WHERE tenant_id=NEW.id;
 DELETE FROM tenant_membership WHERE tenant_id=NEW.id;
 DELETE FROM app_role_permission WHERE role_id IN (SELECT id FROM app_role WHERE tenant_id=NEW.id);
 DELETE FROM app_role WHERE tenant_id=NEW.id;
 FOREACH tab IN ARRAY ARRAY['document_approval_threshold','swift_import_record','charge_profile','integration_outbox'] LOOP
  EXECUTE format('DELETE FROM %I WHERE tenant_id=$1',tab) USING NEW.id;
 END LOOP;
 FOR tab IN SELECT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='tenant_id' AND table_name<>'audit_event' LOOP
  EXECUTE format('SELECT count(*) FROM %I WHERE tenant_id=$1',tab) INTO remaining USING NEW.id;
  IF remaining<>0 THEN RAISE EXCEPTION 'Unresolved tenant data category: %',tab; END IF;
 END LOOP;
 RETURN NEW;
END $$;
ALTER FUNCTION purge_deleted_tenant_business_data() SECURITY DEFINER SET search_path = public;
