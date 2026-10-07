-- Independent access restriction; keep V60 compatibility/write guards and the single-tenant gate.
CREATE TABLE tenant_membership_suspension (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id uuid NOT NULL,
 user_id uuid NOT NULL,
 suspended boolean NOT NULL DEFAULT false,
 CONSTRAINT tenant_membership_suspension_identity UNIQUE(tenant_id,user_id),
 CONSTRAINT tenant_membership_suspension_membership FOREIGN KEY(tenant_id,user_id)
  REFERENCES tenant_membership(tenant_id,user_id) ON DELETE CASCADE
);
