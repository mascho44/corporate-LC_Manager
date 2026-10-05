CREATE TABLE document_comparison (
 id uuid PRIMARY KEY, lc_id uuid NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 before_document_id uuid NOT NULL, after_document_id uuid NOT NULL,
 result_json text NOT NULL, created_by varchar(255) NOT NULL, created_at timestamp NOT NULL
);
CREATE INDEX document_comparison_lc ON document_comparison(lc_id);
