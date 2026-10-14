-- AI suggestions per tenant. No row means off; they also need the global switch (lc.ai.enabled).
CREATE TABLE tenant_ai_setting (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 enabled boolean NOT NULL DEFAULT false,
 changed_by varchar(100),
 changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT tenant_ai_setting_one UNIQUE (tenant_id)
);
