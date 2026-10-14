-- Explicit link of an LC file to a customer of the customer master (optional; without it the name of the customer is compared to beneficiary and applicant).
ALTER TABLE letter_of_credit ADD COLUMN customer_id uuid REFERENCES customer(id) ON DELETE SET NULL;
CREATE INDEX letter_of_credit_customer_idx ON letter_of_credit(customer_id);
