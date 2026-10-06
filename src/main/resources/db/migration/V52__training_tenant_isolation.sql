ALTER TABLE training_learning_control DROP CONSTRAINT training_learning_control_pkey;
ALTER TABLE training_learning_control ADD PRIMARY KEY (tenant_id, rule_id);
ALTER TABLE training_session DROP CONSTRAINT training_session_lc_id_fkey;
ALTER TABLE training_session ADD CONSTRAINT training_session_lc_same_tenant
    FOREIGN KEY (tenant_id, lc_id) REFERENCES letter_of_credit(tenant_id, id)
    ON DELETE SET NULL (lc_id);
CREATE INDEX training_session_tenant_created_idx ON training_session(tenant_id, created_at DESC);
