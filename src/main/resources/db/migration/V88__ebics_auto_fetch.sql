-- Optional scheduled fetch of trade-finance messages per tenant connection (off by default).
ALTER TABLE ebics_connection
 ADD COLUMN auto_fetch boolean NOT NULL DEFAULT false,
 ADD COLUMN fetch_interval_minutes integer NOT NULL DEFAULT 15,
 ADD COLUMN last_fetch_at timestamp,
 ADD COLUMN last_fetch_result varchar(500),
 ADD CONSTRAINT ebics_fetch_interval CHECK (fetch_interval_minutes BETWEEN 5 AND 1440);
