-- A deleted account stays as an empty shell: the user name is kept (history, audit and file records name it) and stays reserved,
-- personal data, credentials and all access are gone.
ALTER TABLE app_user ADD COLUMN deleted_at timestamp;
