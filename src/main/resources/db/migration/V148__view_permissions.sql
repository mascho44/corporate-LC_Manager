-- Reading letters of credit and guarantees can be given separately. Every existing role keeps reading both.
INSERT INTO app_role_permission (role_id, permission)
SELECT id, p FROM app_role CROSS JOIN (VALUES ('LC_VIEW'),('GUARANTEE_VIEW')) AS v(p)
ON CONFLICT (role_id, permission) DO NOTHING;
