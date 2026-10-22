-- API-Schluessel fuer die externe REST-API (/api/v1): je Mandant, nur der Hash wird gespeichert, Berechtigungsumfaenge als Liste.
CREATE TABLE api_key (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 name varchar(100) NOT NULL,
 key_hash varchar(64) NOT NULL,
 key_prefix varchar(12) NOT NULL,
 scopes varchar(100) NOT NULL,
 created_by varchar(100),
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_used_at timestamp,
 revoked_at timestamp,
 CONSTRAINT api_key_hash_unique UNIQUE (key_hash)
);
CREATE INDEX idx_api_key_tenant ON api_key (tenant_id);
-- Die Schluesselsuche vor der Mandantenbestimmung laeuft im ausdruecklichen Plattform-Scope (app.platform = 'on'), wie bei capture_token.
ALTER TABLE api_key ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON api_key
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid OR current_setting('app.platform', true) = 'on')
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid OR current_setting('app.platform', true) = 'on');
-- Die Loeschung eines Mandanten raeumt auch API-Schluessel ab (Funktion aus V139 mit api_key in der Liste).
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
 FOREACH tab IN ARRAY ARRAY['api_key','webhook_delivery','webhook_subscription','correspondent_bank','sla_rule','lc_task','swift_message','workflow','automation_rule','team','ebics_message','ebics_connection','tenant_ai_setting','tenant_scan_profile','document_metadata_training','document_spatial_layout'] LOOP
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
