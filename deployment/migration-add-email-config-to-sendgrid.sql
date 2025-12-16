-- Migration: Add email configuration fields to sendgrid_config table

ALTER TABLE sendgrid_config 
ADD COLUMN IF NOT EXISTS email_from_address VARCHAR(255),
ADD COLUMN IF NOT EXISTS email_from_name VARCHAR(255);

COMMENT ON COLUMN sendgrid_config.email_from_address IS 'Default sender email address for all sites';
COMMENT ON COLUMN sendgrid_config.email_from_name IS 'Default sender name for all sites';




