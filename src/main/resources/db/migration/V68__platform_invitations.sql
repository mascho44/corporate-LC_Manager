ALTER TABLE app_user ADD COLUMN invitation_pending boolean NOT NULL DEFAULT false;
CREATE TABLE platform_invitation (
 token_hash varchar(64) PRIMARY KEY,
 user_id uuid NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
 issuer_id uuid NOT NULL REFERENCES app_user(id),
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 role_id uuid NOT NULL,
 role_stamp varchar(64) NOT NULL,
 credential_stamp varchar(64) NOT NULL,
 email varchar(255) NOT NULL,
 expires_at timestamptz NOT NULL,
 delivery_status varchar(30) NOT NULL,
 CONSTRAINT invitation_role_same_tenant FOREIGN KEY(tenant_id,role_id) REFERENCES app_role(tenant_id,id)
);
