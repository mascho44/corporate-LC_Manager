-- Since when an LC is in status WAITING_FOR_CUSTOMER; maintained by the application on status changes.
ALTER TABLE letter_of_credit ADD COLUMN waiting_since DATE;
