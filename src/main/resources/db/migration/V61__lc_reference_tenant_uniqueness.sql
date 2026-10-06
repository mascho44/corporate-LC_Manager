-- Keep reference uniqueness within its owning tenant. V49's bootstrap gate stays intact.
-- Add the replacement first, so a failed migration never leaves references unprotected.
ALTER TABLE letter_of_credit ADD CONSTRAINT letter_of_credit_tenant_reference_unique
 UNIQUE (tenant_id,reference);
ALTER TABLE letter_of_credit DROP CONSTRAINT letter_of_credit_reference_key;
