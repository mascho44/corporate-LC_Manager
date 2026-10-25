-- Webhooks: Empfaenger-URLs je Mandant (Geheimnis verschluesselt) und die einzelnen Zustellungen mit Wiederholung.
CREATE TABLE webhook_subscription (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 name varchar(120) NOT NULL,
 url varchar(500) NOT NULL,
 secret_enc bytea NOT NULL,
 event_types varchar(400) NOT NULL,
 active boolean NOT NULL DEFAULT true,
 changed_by varchar(100),
 changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_delivery_at timestamp,
 last_status varchar(20),
 CONSTRAINT webhook_subscription_name_unique UNIQUE (tenant_id, name)
);
CREATE TABLE webhook_delivery (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 subscription_id uuid NOT NULL REFERENCES webhook_subscription(id) ON DELETE CASCADE,
 event_id uuid NOT NULL,
 event_type varchar(60) NOT NULL,
 payload text NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'PENDING',
 attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 http_status integer,
 last_error varchar(500),
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 delivered_at timestamp,
 CONSTRAINT webhook_delivery_status CHECK (status IN ('PENDING','DELIVERED','DEAD'))
);
CREATE INDEX webhook_delivery_due ON webhook_delivery(tenant_id, status, next_attempt_at);
CREATE INDEX webhook_delivery_subscription ON webhook_delivery(tenant_id, subscription_id, created_at DESC);
ALTER TABLE webhook_subscription ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON webhook_subscription
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
ALTER TABLE webhook_delivery ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON webhook_delivery
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
-- Die Loeschung eines Mandanten raeumt auch Webhooks ab (Funktion aus V137 mit webhook_delivery und webhook_subscription in der Liste; Zustellungen zuerst).
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
 FOREACH tab IN ARRAY ARRAY['webhook_delivery','webhook_subscription','correspondent_bank','sla_rule','lc_task','swift_message','workflow','automation_rule','team','ebics_message','ebics_connection','tenant_ai_setting','tenant_scan_profile','document_metadata_training','document_spatial_layout'] LOOP
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
