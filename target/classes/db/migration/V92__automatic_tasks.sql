-- Automatic team tasks: rules per tenant and trigger; tasks may now belong to a subject (EBICS message, inbox item) instead of a dossier.
ALTER TABLE lc_task ALTER COLUMN letter_of_credit_id DROP NOT NULL;
ALTER TABLE lc_task
 ADD COLUMN subject_type varchar(20),
 ADD COLUMN subject_id uuid,
 ADD COLUMN source varchar(20) NOT NULL DEFAULT 'MANUAL',
 ADD COLUMN auto_key varchar(120);
CREATE UNIQUE INDEX lc_task_auto_key_unique ON lc_task (tenant_id, auto_key) WHERE auto_key IS NOT NULL;
CREATE INDEX idx_lc_task_subject ON lc_task (tenant_id, subject_type, subject_id);

CREATE TABLE automation_rule (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 trigger_type varchar(20) NOT NULL,
 enabled boolean NOT NULL DEFAULT false,
 team_id uuid REFERENCES team(id),
 lead_days integer NOT NULL DEFAULT 3,
 CONSTRAINT automation_rule_trigger CHECK (trigger_type IN ('EBICS_MESSAGE','INBOX_ITEM','DEADLINE')),
 CONSTRAINT automation_rule_lead CHECK (lead_days BETWEEN 0 AND 60),
 CONSTRAINT automation_rule_unique UNIQUE (tenant_id, trigger_type)
);
