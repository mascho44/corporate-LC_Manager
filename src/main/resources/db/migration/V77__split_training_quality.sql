ALTER TABLE document_split_training ADD COLUMN proposed_parts_json text;
ALTER TABLE document_split_training ADD COLUMN proposed_method varchar(40);
-- Historical confirmations intentionally remain unmeasured: no original proposal was retained.
