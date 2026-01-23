-- Migration: Add database trigger to automatically append status history
-- This ensures history is recorded even when workers update status directly via JDBC
-- Workers (email-worker, whatsapp-worker) update status directly and bypass MessageStatusHistoryService
--
-- ⚠️ PREREQUISITE: Run migration-add-history-source-column.sql FIRST
-- This trigger requires the 'source' column and 'history_source' enum to exist

-- Function to append status history when status changes
-- ⚠️ DEDUPLICATION: Checks if a recent entry already exists to prevent duplicates
-- when both API layer and trigger try to insert the same status change
CREATE OR REPLACE FUNCTION append_message_status_history()
RETURNS TRIGGER AS $$
DECLARE
    v_channel notification_channel;
    v_retry_count INTEGER;
    v_recent_entry_exists BOOLEAN;
BEGIN
    -- Only append if status actually changed
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        -- Get channel and retry_count from the updated row
        v_channel := NEW.channel;
        v_retry_count := COALESCE(NEW.retry_count, 0);
        
        -- ⚠️ DEDUPLICATION: Check if a recent entry (within 1 second) already exists
        -- This prevents duplicate entries when both API layer and trigger try to insert
        -- the same status change (e.g., API updates status, then worker updates same row)
        SELECT EXISTS(
            SELECT 1 FROM message_status_history
            WHERE message_id = NEW.message_id
              AND status = NEW.status
              AND timestamp >= NOW() - INTERVAL '1 second'
        ) INTO v_recent_entry_exists;
        
        -- Only insert if no recent entry exists (deduplication)
        IF NOT v_recent_entry_exists THEN
            INSERT INTO message_status_history (message_id, status, error_message, retry_count, timestamp, source)
            VALUES (NEW.message_id, NEW.status, NEW.error_message, v_retry_count, NOW(), 'TRIGGER'::history_source);
        END IF;
        
        -- Note: Metrics are NOT emitted here because:
        -- 1. Workers are in separate modules and can't access NotificationMetricsService
        -- 2. Metrics are emitted when KafkaRetryService updates status (which goes through MessageStatusHistoryService)
        -- 3. This ensures metrics are only emitted once per status change
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on message_logs table
DROP TRIGGER IF EXISTS trigger_append_status_history ON message_logs;
CREATE TRIGGER trigger_append_status_history
    AFTER UPDATE OF status, error_message, retry_count ON message_logs
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION append_message_status_history();

-- Add comment explaining the trigger
COMMENT ON FUNCTION append_message_status_history() IS 
'Automatically appends status changes to message_status_history table when status is updated directly via SQL/JDBC. This ensures history is recorded even when workers bypass MessageStatusHistoryService. Includes deduplication logic to prevent duplicate entries when both API layer and trigger try to insert the same status change. Metrics are NOT emitted here - they are emitted by MessageStatusHistoryService when status changes go through the API layer.';

