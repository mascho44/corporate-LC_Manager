insert into company_profile(id,updated_at) values (1,current_timestamp) on conflict do nothing;
create sequence company_profile_id_seq start with 2;
select setval('company_profile_id_seq',greatest(2,(select max(id)+1 from company_profile)),false);
alter table letter_of_credit add column company_id integer references company_profile(id);
alter table document_template add column company_id integer references company_profile(id);
alter table document_template drop constraint uq_document_template_company_type;
create unique index uq_template_company_id_type on document_template(company_id,document_type) where company_id is not null;
create unique index uq_template_legacy_name_type on document_template(company_name,document_type) where company_id is null;
