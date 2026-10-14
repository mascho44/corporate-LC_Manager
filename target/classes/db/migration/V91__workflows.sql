-- Workflow instances: a fixed template runs as a chain of team tasks; the next step is created when the current one is completed.
CREATE TABLE workflow (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 lc_id uuid NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 template varchar(30) NOT NULL,
 status varchar(12) NOT NULL DEFAULT 'RUNNING',
 current_step integer NOT NULL DEFAULT 1,
 team_id uuid NOT NULL REFERENCES team(id),
 approval_team_id uuid REFERENCES team(id),
 started_by varchar(100) NOT NULL,
 started_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at timestamp,
 CONSTRAINT workflow_status CHECK (status IN ('RUNNING','DONE','CANCELLED'))
);
CREATE INDEX workflow_lc ON workflow(tenant_id, lc_id);
CREATE UNIQUE INDEX workflow_one_running ON workflow(tenant_id, lc_id, template) WHERE status = 'RUNNING';
ALTER TABLE lc_task
 ADD COLUMN workflow_id uuid REFERENCES workflow(id) ON DELETE CASCADE,
 ADD COLUMN step_no integer,
 ADD COLUMN step_key varchar(30),
 ADD COLUMN four_eyes boolean NOT NULL DEFAULT false,
 ADD COLUMN completed_by varchar(100);
CREATE INDEX idx_lc_task_workflow ON lc_task (workflow_id, step_no);
