-- Separate right to edit guarantees. Every role that may edit letters of credit keeps working as before: it gets the new right too.
INSERT INTO app_role_permission (role_id, permission)
SELECT role_id, 'GUARANTEE_EDIT' FROM app_role_permission WHERE permission = 'LC_EDIT'
ON CONFLICT (role_id, permission) DO NOTHING;
