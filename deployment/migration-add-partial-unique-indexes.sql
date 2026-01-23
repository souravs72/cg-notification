-- Migration: Add partial unique indexes for soft-delete support
-- These indexes ensure uniqueness only for non-deleted rows, preventing
-- duplicate active records while allowing reuse of values after soft-delete

-- ============================================================================
-- 1. frappe_sites: Prevent duplicate active API key hashes
-- ============================================================================
-- This prevents race-condition inserts and duplicate active keys
-- Note: api_key_hash is used for authentication lookups, so active keys must be unique
CREATE UNIQUE INDEX IF NOT EXISTS uq_frappe_sites_api_key_hash
ON frappe_sites(api_key_hash)
WHERE is_deleted = false;

-- ============================================================================
-- 2. users: Prevent duplicate active email addresses (case-insensitive)
-- ============================================================================
-- This allows email reuse after soft-delete while preventing duplicate active users
-- Uses lower(email) for case-insensitive uniqueness (prevents sourav@example.com and Sourav@example.com)
-- 
-- Note: The table-level UNIQUE constraint on email prevents reuse until soft-delete is removed
-- This partial index provides additional protection for active records and prevents race conditions
-- 
-- Optional: To fully enable email reuse after soft-delete, you would need to:
-- 1. Drop the table-level UNIQUE constraint: ALTER TABLE users DROP CONSTRAINT users_email_key;
-- 2. Keep only this partial unique index
-- 
-- For now, keeping both for maximum protection
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_active
ON users(lower(email))
WHERE is_deleted = false;

-- ============================================================================
-- 3. whatsapp_sessions: Prevent duplicate active (user_id, session_name) pairs
-- ============================================================================
-- The existing table-level UNIQUE(user_id, session_name) prevents reuse even after soft-delete
-- Replace it with a partial unique index to allow session name reuse after deletion
-- 
-- Step 1: Drop the existing unique constraint
-- Note: PostgreSQL creates a unique index automatically for UNIQUE constraints
-- from CREATE TABLE ... UNIQUE(user_id, session_name)
-- The constraint name is typically: whatsapp_sessions_user_id_session_name_key
-- 
-- We'll try to drop it directly, and if that fails, find it dynamically
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    -- First, try the standard naming convention
    BEGIN
        ALTER TABLE whatsapp_sessions DROP CONSTRAINT IF EXISTS whatsapp_sessions_user_id_session_name_key;
        -- If successful, we're done
        RETURN;
    EXCEPTION WHEN undefined_object THEN
        -- Constraint doesn't exist with that name, find it dynamically
        NULL;
    END;
    
    -- Find the unique constraint on (user_id, session_name) dynamically
    SELECT conname INTO constraint_name
    FROM pg_constraint c
    WHERE c.conrelid = 'whatsapp_sessions'::regclass
    AND c.contype = 'u'
    AND (
        -- Check if constraint covers exactly user_id and session_name
        SELECT COUNT(*) = 2
        FROM pg_attribute a
        WHERE a.attrelid = c.conrelid
        AND a.attnum = ANY(c.conkey)
        AND a.attname IN ('user_id', 'session_name')
    );
    
    -- Drop the constraint if found
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE whatsapp_sessions DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Step 2: Create partial unique index that respects soft-deletes
CREATE UNIQUE INDEX IF NOT EXISTS uq_whatsapp_sessions_user_session_name_active
ON whatsapp_sessions(user_id, session_name)
WHERE is_deleted = false;

-- Add comment explaining the change
COMMENT ON INDEX uq_whatsapp_sessions_user_session_name_active IS 
'Ensures unique (user_id, session_name) pairs only for active (non-deleted) sessions. Allows session name reuse after soft-delete.';

