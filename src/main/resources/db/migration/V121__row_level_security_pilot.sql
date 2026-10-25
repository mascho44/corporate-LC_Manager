-- Row-Level-Security, Schritt 2 (Pilot): zwei Mandanten-Tabellen. Die App meldet den Mandanten pro Verbindung in
-- app.tenant_id (TenantSessionDataSource). Ohne Variable (NULL) sieht und aendert die Laufzeitrolle nichts.
-- Der Tabellenbesitzer (Flyway) ist bewusst nicht per FORCE gebunden.
ALTER TABLE learned_field_label ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_review_step ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON learned_field_label
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON lc_review_step
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Die Loesch-Trigger laufen sonst mit den Rechten des Aufrufers und sahen unter RLS den geloeschten Mandanten nicht.
ALTER FUNCTION purge_deleted_tenant_learned_field_label() SECURITY DEFINER SET search_path = public;
ALTER FUNCTION purge_deleted_tenant_lc_review_step() SECURITY DEFINER SET search_path = public;
