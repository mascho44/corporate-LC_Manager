-- Which side of the credit the tenant is on for this file: exporter (beneficiary, default) or importer (applicant).
ALTER TABLE letter_of_credit ADD COLUMN party_role varchar(12) NOT NULL DEFAULT 'BENEFICIARY';
ALTER TABLE letter_of_credit ADD CONSTRAINT letter_of_credit_party_role CHECK (party_role IN ('BENEFICIARY','APPLICANT'));
