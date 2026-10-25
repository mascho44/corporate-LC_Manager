-- Teams (group inbox): tasks can be handed to a team; a member claims the task, which sets the assignee.
CREATE TABLE team (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 name varchar(100) NOT NULL,
 description varchar(300),
 active boolean NOT NULL DEFAULT true,
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX team_name_unique ON team(tenant_id, lower(name));
CREATE TABLE team_member (
 team_id uuid NOT NULL REFERENCES team(id) ON DELETE CASCADE,
 username varchar(100) NOT NULL,
 PRIMARY KEY (team_id, username)
);
ALTER TABLE lc_task ADD COLUMN team_id uuid REFERENCES team(id), ADD COLUMN claimed_at timestamp;
CREATE INDEX idx_lc_task_team_open ON lc_task (team_id, completed);
