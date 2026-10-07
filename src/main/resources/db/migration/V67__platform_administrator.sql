-- Explicit first platform administrator selected by the operator: admin.
-- Local ADMIN roles and USER_MANAGE permissions never imply this global grant.
ALTER TABLE app_user ADD COLUMN platform_administrator boolean NOT NULL DEFAULT false;
UPDATE app_user SET platform_administrator=true
WHERE tenant_id='00000000-0000-0000-0000-000000000001' AND lower(username)='admin';
