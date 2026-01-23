-- Migration: Add message_status_history table for append-only status tracking
-- Provides audit trail, retry timelines, and failure analysis

CREATE TABLE IF NOT EXISTS message_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id VARCHAR(100) NOT NULL,
    status delivery_status NOT NULL,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_message_status_history_message_id 
ON message_status_history(message_id);

CREATE INDEX IF NOT EXISTS idx_message_status_history_timestamp 
ON message_status_history(timestamp);

-- Composite index for common queries (message history by message_id ordered by timestamp)
CREATE INDEX IF NOT EXISTS idx_message_status_history_message_timestamp 
ON message_status_history(message_id, timestamp);

-- Add comment explaining append-only nature
COMMENT ON TABLE message_status_history IS 
'Append-only history of message status changes. Provides audit trail, retry timelines, and failure analysis. Never updated or deleted.';

COMMENT ON COLUMN message_status_history.message_id IS 
'References message_logs.message_id for joining with current message state.';

COMMENT ON COLUMN message_status_history.status IS 
'Status at the time of this history entry.';

COMMENT ON COLUMN message_status_history.timestamp IS 
'When this status change occurred.';

