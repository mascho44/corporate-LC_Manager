-- Row-Level-Security, Schritt 3: Eingangskanaele und Herkunft der Posteingangs-Dokumente (siehe V121).
ALTER TABLE intake_channel ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbox_capture_info ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON intake_channel
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON inbox_capture_info
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Loesch-Trigger muessen die Zeilen des geloeschten Mandanten unabhaengig vom Kontext sehen.
ALTER FUNCTION purge_deleted_tenant_intake_channel() SECURITY DEFINER SET search_path = public;
ALTER FUNCTION purge_recent_tenant_tables() SECURITY DEFINER SET search_path = public;
