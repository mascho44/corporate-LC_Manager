-- How a document reached the inbox through a scan station: source, batch, scanner, declared and real page count, and its checksum (a second delivery of the same file is recognised).
CREATE TABLE inbox_capture_info (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 inbox_item_id uuid NOT NULL REFERENCES document_inbox_item(id) ON DELETE CASCADE,
 source varchar(100) NOT NULL,
 batch_id varchar(100),
 scanner varchar(100),
 declared_pages integer,
 actual_pages integer,
 scanned_at timestamp,
 sha256 varchar(64) NOT NULL,
 received_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT inbox_capture_info_item UNIQUE (inbox_item_id),
 CONSTRAINT inbox_capture_info_sha UNIQUE (tenant_id, sha256)
);
