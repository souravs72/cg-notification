-- Migration: Add failure_type column to message_logs table
-- This allows efficient querying of retry candidates without filtering in Java

-- Create failure_type enum
DO $$ BEGIN
    CREATE TYPE failure_type AS ENUM ('KAFKA', 'CONSUMER');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Add failure_type column (nullable, only set when status is FAILED)
ALTER TABLE message_logs 
ADD COLUMN IF NOT EXISTS failure_type failure_type;

-- Create index for efficient retry queries
CREATE INDEX IF NOT EXISTS idx_failure_type ON message_logs(failure_type) 
WHERE failure_type IS NOT NULL;

-- Create composite index for efficient retry queries (status, failure_type, created_at)
-- This optimizes queries that filter by status and failure_type, ordered by created_at
CREATE INDEX IF NOT EXISTS idx_retry_query ON message_logs(status, failure_type, created_at) 
WHERE status = 'FAILED' AND failure_type IS NOT NULL;

-- Backfill existing FAILED messages based on error_message pattern
-- Messages with "Kafka" in error_message are KAFKA failures, others are CONSUMER
UPDATE message_logs 
SET failure_type = CASE 
    WHEN status = 'FAILED' AND error_message LIKE '%Kafka%' THEN 'KAFKA'::failure_type
    WHEN status = 'FAILED' AND error_message IS NOT NULL THEN 'CONSUMER'::failure_type
    ELSE NULL
END
WHERE status = 'FAILED' AND failure_type IS NULL;

-- Note: The constraint chk_failure_type_consistency is added in a separate migration
-- (migration-add-failure-type-constraint.sql) to allow backfilling data first

