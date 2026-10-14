-- Corporate procurement plan: lead times per tenant and document type, and the manual status of a required document per LC.
CREATE TABLE procurement_lead_time (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 document_type varchar(60) NOT NULL,
 working_days integer NOT NULL CHECK (working_days BETWEEN 0 AND 60),
 changed_by varchar(100),
 changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT procurement_lead_time_one UNIQUE (tenant_id, document_type)
);
CREATE TABLE procurement_item_status (
 id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL REFERENCES tenant(id),
 letter_of_credit_id uuid NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
 requirement varchar(2000) NOT NULL,
 status varchar(20) NOT NULL,
 note varchar(500),
 changed_by varchar(100),
 changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT procurement_item_status_one UNIQUE (letter_of_credit_id, requirement)
);
