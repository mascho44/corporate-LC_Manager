-- Existing users remain able to sign in and supply a contact address in their profile.
-- All new/edited accounts require email through application validation.
ALTER TABLE app_user ADD COLUMN email VARCHAR(255);
