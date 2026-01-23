-- Migration: Create sendgrid_config table to store global SendGrid API key configuration
-- This table stores the global SendGrid API key that will be used for all sites

CREATE TABLE IF NOT EXISTS sendgrid_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sendgrid_api_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create index for active config lookup
CREATE INDEX IF NOT EXISTS idx_sendgrid_config_active ON sendgrid_config(is_deleted) WHERE is_deleted = FALSE;

-- Add comment
COMMENT ON TABLE sendgrid_config IS 'Stores global SendGrid API key configuration used for all sites';
COMMENT ON COLUMN sendgrid_config.sendgrid_api_key IS 'Global SendGrid API key used for sending emails from all sites';







