create table app_role (
    id uuid primary key,
    name varchar(100) not null unique,
    system_role boolean not null default false,
    base_role varchar(30) not null
);
create table app_role_permission (
    role_id uuid not null references app_role(id) on delete cascade,
    permission varchar(50) not null,
    primary key (role_id, permission)
);
alter table app_user add column assigned_role_id uuid references app_role(id);
insert into app_role(id,name,system_role,base_role) values
('00000000-0000-0000-0000-000000000001','Administrator',true,'ADMIN'),
('00000000-0000-0000-0000-000000000002','Sachbearbeiter',true,'EDITOR'),
('00000000-0000-0000-0000-000000000003','Leser',true,'VIEWER');
insert into app_role_permission(role_id,permission)
select '00000000-0000-0000-0000-000000000001', permission from unnest(array['LC_EDIT','LC_DELETE','SWIFT_IMPORT','DOCUMENT_UPLOAD','DOCUMENT_DELETE','DOCUMENT_GENERATE','DOCUMENT_REVIEW','TRAINING_MANAGE','USER_MANAGE','AUDIT_VIEW','SETTINGS_MANAGE']) permission;
insert into app_role_permission(role_id,permission)
select '00000000-0000-0000-0000-000000000002', permission from unnest(array['LC_EDIT','SWIFT_IMPORT','DOCUMENT_UPLOAD','DOCUMENT_DELETE','DOCUMENT_GENERATE','DOCUMENT_REVIEW','TRAINING_MANAGE']) permission;
update app_user set assigned_role_id=case when role='ADMIN' then '00000000-0000-0000-0000-000000000001'::uuid when role in ('EDITOR','USER') then '00000000-0000-0000-0000-000000000002'::uuid else '00000000-0000-0000-0000-000000000003'::uuid end;
alter table app_user alter column assigned_role_id set not null;
