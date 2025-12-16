-- Migration: Make site_id nullable in message_logs table
-- This allows sending messages from dashboard without site association
-- Site is still required when using APIs from Frappe app

ALTER TABLE message_logs 
ALTER COLUMN site_id DROP NOT NULL;

-- Add comment
COMMENT ON COLUMN message_logs.site_id IS 'Site ID (nullable for dashboard messages, required for API calls from Frappe app)';





