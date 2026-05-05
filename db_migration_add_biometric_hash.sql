-- Migration script to add 'hashed' column to biometric table (Room/SQLite)
-- Place this in your Room database migration logic or run as a one-off migration

ALTER TABLE biometric ADD COLUMN hashed TEXT;

-- Optionally, backfill the hash for existing rows (requires custom script in app)
-- You may need to iterate all rows in Kotlin and update the hash field after this migration.