ALTER TABLE company_profile ADD CONSTRAINT company_profile_tenant_id_unique UNIQUE(tenant_id,id);
ALTER TABLE letter_of_credit ADD CONSTRAINT lc_company_same_tenant
 FOREIGN KEY(tenant_id,company_id) REFERENCES company_profile(tenant_id,id);
ALTER TABLE document_template ADD CONSTRAINT document_template_company_same_tenant
 FOREIGN KEY(tenant_id,company_id) REFERENCES company_profile(tenant_id,id);
DROP INDEX uq_template_company_id_type;
DROP INDEX uq_template_legacy_name_type;
CREATE UNIQUE INDEX uq_template_company_id_type ON document_template(tenant_id,company_id,document_type) WHERE company_id IS NOT NULL;
CREATE UNIQUE INDEX uq_template_legacy_name_type ON document_template(tenant_id,company_name,document_type) WHERE company_id IS NULL;
