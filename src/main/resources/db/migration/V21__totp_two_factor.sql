alter table app_user add column totp_enabled boolean not null default false;
alter table app_user add column totp_secret_encrypted text;
alter table app_user add column recovery_code_hashes text;
