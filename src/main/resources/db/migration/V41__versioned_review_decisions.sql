alter table document_check_decision add column finding_fingerprint varchar(64);
alter table document_check_decision add column rule_catalog_version varchar(40);
alter table document_check_decision add column rule_id varchar(100);
alter table document_check_decision add column rule_version varchar(40);
alter table document_check_decision add column finding_snapshot text;
alter table document_check_decision add column invalidated_at timestamp;
-- Existing decisions are retained but cannot silently override a newly evaluated finding.
alter table document_check_decision drop constraint uk_document_check_decision;
alter table document_check_decision add constraint uk_versioned_check_decision
    unique (lc_id,finding_code,document_name,finding_fingerprint);
