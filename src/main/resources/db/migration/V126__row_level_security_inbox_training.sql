-- Row-Level-Security, Schritt 7: Posteingang, Entwuerfe und Trainingsdaten. Siehe V121.
ALTER TABLE document_inbox_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_draft ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_metadata_training ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_spatial_layout ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_split_training ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_type_example ENABLE ROW LEVEL SECURITY;
ALTER TABLE training_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE training_learning_control ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_inbox_item
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_draft
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_metadata_training
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_spatial_layout
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_split_training
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON document_type_example
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON training_session
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation ON training_learning_control
 USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
 WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
