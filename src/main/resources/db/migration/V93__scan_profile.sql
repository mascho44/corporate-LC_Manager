-- Scan profile per tenant (how scanned pages are rendered and prepared for recognition). No row means STANDARD.
CREATE TABLE tenant_scan_profile (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 profile varchar(30) NOT NULL,
 changed_by varchar(100),
 changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT tenant_scan_profile_one UNIQUE (tenant_id),
 CONSTRAINT tenant_scan_profile_known CHECK (profile IN ('STANDARD','PROFI_SCANNER','SCHLECHTER_SCAN'))
);
