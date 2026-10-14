-- Role names belong to a tenant; identities remain globally unique.
-- The bootstrap gate and same-tenant user/membership role FKs stay unchanged.
ALTER TABLE app_role ADD CONSTRAINT app_role_tenant_name_unique UNIQUE (tenant_id,name);
ALTER TABLE app_role DROP CONSTRAINT app_role_name_key;
