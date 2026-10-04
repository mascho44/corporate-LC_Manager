create table document_approval_threshold (
    id uuid primary key,
    currency varchar(3) not null,
    minimum_amount numeric(19, 2) not null check (minimum_amount >= 0),
    required_approvals integer not null check (required_approvals between 1 and 5),
    constraint uq_document_approval_threshold unique (currency, minimum_amount)
);

alter table document_draft
    add column required_approvals integer not null default 1 check (required_approvals between 1 and 5),
    add column approvals_json text not null default '[]';

insert into app_role_permission (role_id, permission)
select id, 'DOCUMENT_APPROVE' from app_role where base_role = 'ADMIN'
on conflict (role_id, permission) do nothing;
