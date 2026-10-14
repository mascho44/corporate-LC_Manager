create table charge_profile(id uuid primary key,name varchar(100) not null,bank_name varchar(255),currency varchar(3) not null,rules_json text not null,created_by varchar(100) not null,created_at timestamp not null);
create table charge_estimate(id uuid primary key,lc_id uuid not null references letter_of_credit(id) on delete cascade,profile_id uuid not null references charge_profile(id),result_json text not null,profile_snapshot text not null,created_by varchar(100) not null,created_at timestamp not null);
create index ix_charge_estimate_lc on charge_estimate(lc_id,created_at);
