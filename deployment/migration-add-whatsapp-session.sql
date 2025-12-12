-- Migration: Add WhatsApp session name to frappe_sites table
-- Also fix api_key column length to match entity definition

-- Add whatsapp_session_name column
ALTER TABLE frappe_sites 
ADD COLUMN IF NOT EXISTS whatsapp_session_name VARCHAR(255);

-- Update api_key column length from 64 to 128 to match entity
ALTER TABLE frappe_sites 
ALTER COLUMN api_key TYPE VARCHAR(128);

-- Create index for WhatsApp session lookups
CREATE INDEX IF NOT EXISTS idx_whatsapp_session ON frappe_sites(whatsapp_session_name);

