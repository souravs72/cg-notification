-- Initialize database for integration tests
-- This script runs before Hibernate creates tables

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create enum types (must be created before Hibernate creates tables)
DO $$ BEGIN
    CREATE TYPE delivery_status AS ENUM ('PENDING', 'RETRYING', 'SCHEDULED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED', 'REJECTED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE notification_channel AS ENUM ('EMAIL', 'WHATSAPP', 'SMS', 'PUSH');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;
