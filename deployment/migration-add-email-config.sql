-- Migration: Add email configuration fields to frappe_sites table

-- Add email configuration columns
ALTER TABLE frappe_sites 
ADD COLUMN IF NOT EXISTS email_from_address VARCHAR(255),
ADD COLUMN IF NOT EXISTS email_from_name VARCHAR(255),
ADD COLUMN IF NOT EXISTS sendgrid_api_key VARCHAR(255);

-- Create index for email lookups
CREATE INDEX IF NOT EXISTS idx_email_from ON frappe_sites(email_from_address);

