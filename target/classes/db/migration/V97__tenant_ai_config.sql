-- Per-tenant AI provider configuration. The API key is stored encrypted (AES-256-GCM, row id authenticated) and never returned.
ALTER TABLE tenant_ai_setting
 ADD COLUMN provider varchar(30),
 ADD COLUMN base_url varchar(300),
 ADD COLUMN model varchar(100),
 ADD COLUMN api_key_cipher text,
 ADD COLUMN timeout_seconds integer;
