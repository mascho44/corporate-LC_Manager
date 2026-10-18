-- Row-Level-Security, Schritt 4: Pruefdaten (Entscheidungen, Notizen, Anforderungszuordnung, Vergleiche, Sanktionsfreigaben). Siehe V121.
ALTER TABLE document_check_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_note ENABLE ROW LEVEL SECURITY;
ALTER TABLE lc_requirement_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_comparison ENABLE ROW LEVEL SECURITY;
ALTER TABLE sanctions_clearance ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_check_decision
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON lc_note
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON lc_requirement_mapping
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_comparison
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON sanctions_clearance
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- Fremdschluessel-Kaskaden umgehen RLS; nur die ausdrueckliche Loeschfunktion braucht die Definer-Rechte.
ALTER FUNCTION purge_deleted_tenant_sanctions_clearance() SECURITY DEFINER SET search_path = public;
