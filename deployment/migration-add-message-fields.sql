-- Migration script to add new message content fields
-- Run this if you have an existing database

-- Fix api_key column length if needed
ALTER TABLE frappe_sites ALTER COLUMN api_key TYPE VARCHAR(255);

-- Add SCHEDULED status to delivery_status enum
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'delivery_status' AND 
                   EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'SCHEDULED' AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'delivery_status'))) THEN
        ALTER TYPE delivery_status ADD VALUE 'SCHEDULED';
    END IF;
END $$;

-- Add new columns to message_logs table
ALTER TABLE message_logs 
    ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS video_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS document_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS caption TEXT,
    ADD COLUMN IF NOT EXISTS from_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS from_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_html BOOLEAN DEFAULT FALSE;

