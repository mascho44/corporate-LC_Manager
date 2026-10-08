ALTER TABLE platform_invitation ADD COLUMN encrypted_mail_payload text;
ALTER TABLE platform_invitation ADD COLUMN delivery_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE platform_invitation ADD COLUMN next_delivery_attempt timestamptz;
ALTER TABLE platform_invitation ADD COLUMN delivery_error varchar(40);
CREATE INDEX platform_invitation_mail_due ON platform_invitation(next_delivery_attempt) WHERE encrypted_mail_payload IS NOT NULL;
