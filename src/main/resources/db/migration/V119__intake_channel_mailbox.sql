-- Mailbox channel: non-secret settings as JSON, the password encrypted (AES-GCM, key from the environment).
ALTER TABLE intake_channel ADD COLUMN settings text;
ALTER TABLE intake_channel ADD COLUMN secret bytea;
