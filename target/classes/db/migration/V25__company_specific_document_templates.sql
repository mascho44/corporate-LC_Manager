alter table document_template add column company_name varchar(255) not null default '*';
alter table document_template drop constraint document_template_document_type_key;
alter table document_template add constraint uq_document_template_company_type unique(company_name,document_type);
