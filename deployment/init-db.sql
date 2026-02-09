-- Create database and extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create enum types
CREATE TYPE delivery_status AS ENUM ('PENDING', 'RETRYING', 'SCHEDULED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED', 'REJECTED');
CREATE TYPE notification_channel AS ENUM ('EMAIL', 'WHATSAPP', 'SMS', 'PUSH');

-- Create frappe_sites table
CREATE TABLE IF NOT EXISTS frappe_sites (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    site_name VARCHAR(255) NOT NULL UNIQUE,
    api_key VARCHAR(64) NOT NULL UNIQUE,
    api_key_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_site_key ON frappe_sites(api_key_hash);
CREATE INDEX idx_site_name ON frappe_sites(site_name);

-- Create message_logs table
CREATE TABLE IF NOT EXISTS message_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id VARCHAR(100) NOT NULL UNIQUE,
    site_id UUID NOT NULL REFERENCES frappe_sites(id),
    channel notification_channel NOT NULL,
    status delivery_status NOT NULL DEFAULT 'PENDING',
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    metadata JSONB,
    image_url VARCHAR(1000),
    video_url VARCHAR(1000),
    document_url VARCHAR(1000),
    file_name VARCHAR(255),
    caption TEXT,
    from_email VARCHAR(255),
    from_name VARCHAR(255),
    is_html BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_site_id ON message_logs(site_id);
CREATE INDEX idx_channel ON message_logs(channel);
CREATE INDEX idx_status ON message_logs(status);
CREATE INDEX idx_created_at ON message_logs(created_at);
CREATE INDEX idx_message_id ON message_logs(message_id);

-- Create site_metrics_daily table
CREATE TABLE IF NOT EXISTS site_metrics_daily (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    site_id UUID NOT NULL REFERENCES frappe_sites(id),
    metric_date DATE NOT NULL,
    channel VARCHAR(20) NOT NULL,
    total_sent BIGINT NOT NULL DEFAULT 0,
    total_success BIGINT NOT NULL DEFAULT 0,
    total_failed BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(site_id, metric_date, channel)
);

CREATE INDEX idx_site_date ON site_metrics_daily(site_id, metric_date);
CREATE INDEX idx_metric_date ON site_metrics_daily(metric_date);

-- Create function to update metrics
CREATE OR REPLACE FUNCTION update_daily_metrics()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO site_metrics_daily (site_id, metric_date, channel, total_sent, total_success, total_failed)
    VALUES (
        NEW.site_id,
        DATE(NEW.created_at),
        NEW.channel::text,
        1,
        CASE WHEN NEW.status = 'DELIVERED' THEN 1 ELSE 0 END,
        CASE WHEN NEW.status IN ('FAILED', 'BOUNCED', 'REJECTED') THEN 1 ELSE 0 END
    )
    ON CONFLICT (site_id, metric_date, channel)
    DO UPDATE SET
        total_sent = site_metrics_daily.total_sent + 1,
        total_success = site_metrics_daily.total_success + 
            CASE WHEN NEW.status = 'DELIVERED' THEN 1 ELSE 0 END,
        total_failed = site_metrics_daily.total_failed + 
            CASE WHEN NEW.status IN ('FAILED', 'BOUNCED', 'REJECTED') THEN 1 ELSE 0 END,
        updated_at = CURRENT_TIMESTAMP;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-update metrics
CREATE TRIGGER trigger_update_metrics
AFTER INSERT ON message_logs
FOR EACH ROW
WHEN (NEW.status IN ('SENT', 'DELIVERED', 'FAILED', 'BOUNCED', 'REJECTED'))
EXECUTE FUNCTION update_daily_metrics();

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for updated_at
CREATE TRIGGER trigger_frappe_sites_updated_at
BEFORE UPDATE ON frappe_sites
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_message_logs_updated_at
BEFORE UPDATE ON message_logs
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_site_metrics_daily_updated_at
BEFORE UPDATE ON site_metrics_daily
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

