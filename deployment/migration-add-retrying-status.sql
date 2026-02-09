-- Migration: Add RETRYING status to delivery_status enum
-- Provides semantic clarity: distinguishes initial PENDING from retry-in-progress

-- Add RETRYING to the enum type
ALTER TYPE delivery_status ADD VALUE IF NOT EXISTS 'RETRYING';

-- Add comment explaining the semantic distinction
COMMENT ON TYPE delivery_status IS 
'Delivery status enum. PENDING = initial message queued, RETRYING = message being retried after failure';

