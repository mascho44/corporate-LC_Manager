-- Public sanctions lists (EU, UN, OFAC) as downloaded. Global reference data, not owned by a tenant.
-- Every screening refers to the stored version (source, fetch time, SHA-256), so a decision can be traced to the exact list content.
CREATE TABLE sanctions_list (
    id uuid PRIMARY KEY,
    source varchar(20) NOT NULL,
    fetched_at timestamp NOT NULL,
    sha256 varchar(64) NOT NULL,
    entry_count integer NOT NULL,
    published varchar(60),
    content bytea NOT NULL
);
CREATE INDEX sanctions_list_source_fetched ON sanctions_list (source, fetched_at DESC);
