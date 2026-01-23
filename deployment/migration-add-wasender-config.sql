-- Migration: Create wasender_config table to store global WASender API key configuration
-- This table stores the global WASender API key that will be used for all WhatsApp sessions

CREATE TABLE IF NOT EXISTS wasender_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wasender_api_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create index for active config lookup
CREATE INDEX IF NOT EXISTS idx_wasender_config_active ON wasender_config(is_deleted) WHERE is_deleted = FALSE;

-- Add comment
COMMENT ON TABLE wasender_config IS 'Stores global WASender API key configuration used for all WhatsApp sessions';
COMMENT ON COLUMN wasender_config.wasender_api_key IS 'Global WASender API key used for sending WhatsApp messages from all sessions';


