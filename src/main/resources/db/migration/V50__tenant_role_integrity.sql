-- Preserve the single-tenant gate from V49. No tenant activation in this migration.
ALTER TABLE app_role ADD CONSTRAINT app_role_tenant_id_unique UNIQUE (tenant_id,id);
ALTER TABLE app_user ADD CONSTRAINT app_user_role_same_tenant
 FOREIGN KEY (tenant_id,assigned_role_id) REFERENCES app_role(tenant_id,id);
