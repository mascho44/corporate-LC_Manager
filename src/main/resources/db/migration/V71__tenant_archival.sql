ALTER TABLE tenant ADD COLUMN archived_at timestamp with time zone;
ALTER TABLE tenant ADD CONSTRAINT tenant_archive_requires_suspension CHECK (archived_at IS NULL OR active=false);
ALTER TABLE tenant ADD CONSTRAINT tenant_default_not_archived CHECK (id<>'00000000-0000-0000-0000-000000000001' OR archived_at IS NULL);
