create table company_profile (
    id integer primary key, legal_name varchar(255), address_line varchar(255), postal_code varchar(30), city varchar(120), country varchar(120),
    email varchar(255), phone varchar(80), tax_id varchar(100), registration_number varchar(100), bank_name varchar(255), iban varchar(100), bic varchar(50),
    contact_person varchar(255), logo_content_type varchar(100), logo bytea, updated_by varchar(100), updated_at timestamp not null
);
