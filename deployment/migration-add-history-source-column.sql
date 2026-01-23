-- Migration: Add source column to message_status_history for deduplication
-- This tracks where the history entry came from (API, TRIGGER, WORKER) to prevent duplicates

-- Create enum for history source
DO $$ BEGIN
    CREATE TYPE history_source AS ENUM ('API', 'TRIGGER', 'WORKER');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Add source column with default 'TRIGGER' (for existing rows and trigger-created entries)
ALTER TABLE message_status_history 
ADD COLUMN IF NOT EXISTS source history_source NOT NULL DEFAULT 'TRIGGER';

-- Update existing rows that were created via API to have source = 'API'
-- (This is a best-effort update - we can't perfectly determine, but API-created entries
--  typically have more complete data. New entries will have correct source.)
UPDATE message_status_history 
SET source = 'API'::history_source 
WHERE source = 'TRIGGER'::history_source 
  AND error_message IS NOT NULL 
  AND LENGTH(error_message) > 50;  -- Heuristic: API entries often have detailed error messages

-- Create index for deduplication queries
CREATE INDEX IF NOT EXISTS idx_history_source ON message_status_history(source);
CREATE INDEX IF NOT EXISTS idx_history_message_status_timestamp 
ON message_status_history(message_id, status, timestamp);

-- Add comment explaining the source column
COMMENT ON COLUMN message_status_history.source IS 
'Source of the history entry: API (from MessageStatusHistoryService), TRIGGER (from database trigger), or WORKER (from worker service). Used for deduplication and audit trail.';






