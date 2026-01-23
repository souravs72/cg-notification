-- Migration: Create whatsapp_sessions table to store session information and API keys
-- This table stores each WhatsApp session with its generated API key

CREATE TABLE IF NOT EXISTS whatsapp_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(100) NOT NULL, -- WASender session ID
    session_name VARCHAR(255) NOT NULL, -- Session name
    session_api_key VARCHAR(500), -- API key generated for this session when connected
    phone_number VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, NEED_SCAN, CONNECTING, CONNECTED, DISCONNECTED
    account_protection BOOLEAN DEFAULT true,
    log_messages BOOLEAN DEFAULT true,
    webhook_url VARCHAR(1000),
    webhook_events TEXT[], -- Array of webhook event types
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    connected_at TIMESTAMP, -- When session was connected
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(user_id, session_name)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_whatsapp_sessions_user_id ON whatsapp_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_sessions_session_id ON whatsapp_sessions(session_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_sessions_status ON whatsapp_sessions(status);
CREATE INDEX IF NOT EXISTS idx_whatsapp_sessions_active ON whatsapp_sessions(user_id, is_deleted) WHERE is_deleted = FALSE;

-- Add comment
COMMENT ON TABLE whatsapp_sessions IS 'Stores WhatsApp session information including API keys generated when sessions connect';
COMMENT ON COLUMN whatsapp_sessions.session_api_key IS 'API key generated for this specific session when it connects. Used for sending messages from this session.';










