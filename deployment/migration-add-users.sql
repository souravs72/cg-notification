-- Create users table for authentication
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    wasender_api_key VARCHAR(500),
    subscription_type VARCHAR(50) DEFAULT 'FREE_TRIAL',
    subscription_status VARCHAR(50) DEFAULT 'ACTIVE',
    sessions_allowed INTEGER DEFAULT 10,
    sessions_used INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_active ON users(is_deleted) WHERE is_deleted = FALSE;

-- Create trigger for updated_at
CREATE TRIGGER trigger_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

