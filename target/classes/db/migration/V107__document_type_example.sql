-- Human-confirmed examples for recognising the document type of a page or document: a bounded set of words plus a short sample of the text, per tenant.
CREATE TABLE document_type_example (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 document_type varchar(60) NOT NULL,
 terms text NOT NULL,
 sample varchar(200),
 confirmed_by varchar(255) NOT NULL,
 confirmed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX document_type_example_lookup ON document_type_example(tenant_id,document_type);
CREATE TRIGGER tenant_purge_write_barrier BEFORE INSERT OR UPDATE ON document_type_example
 FOR EACH ROW EXECUTE FUNCTION guard_deleted_tenant_write();
