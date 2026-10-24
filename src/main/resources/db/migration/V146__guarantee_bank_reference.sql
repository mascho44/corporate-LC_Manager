-- The bank's own reference (guarantee number) of an issued guarantee, taken from the copy or entered by hand.
ALTER TABLE guarantee_detail ADD COLUMN bank_reference varchar(100);
