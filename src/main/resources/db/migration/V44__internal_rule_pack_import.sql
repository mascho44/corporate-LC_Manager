create table internal_rule_pack_version (
 id uuid primary key,
 pack_id varchar(31) not null,
 pack_version varchar(11) not null,
 definition_json text not null,
 checksum varchar(64) not null,
 tests_passed boolean not null,
 imported_by varchar(100) not null,
 imported_at timestamp not null,
 unique(pack_id,pack_version)
);
create table internal_rule_pack (
 id varchar(31) primary key,
 active_version_id uuid references internal_rule_pack_version(id),
 previous_version_id uuid references internal_rule_pack_version(id),
 lock_version bigint not null default 0
);
