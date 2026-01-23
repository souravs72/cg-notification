-- Migration: Add constraint to enforce status + failure_type invariant
-- This ensures data consistency at the database level

-- Add constraint to enforce that:
-- - FAILED status must have failure_type set
-- - Non-FAILED status must have failure_type NULL
ALTER TABLE message_logs
ADD CONSTRAINT chk_failure_type_consistency
CHECK (
  (status = 'FAILED'::delivery_status AND failure_type IS NOT NULL)
  OR (status <> 'FAILED'::delivery_status AND failure_type IS NULL)
);

-- Add comment explaining the constraint
COMMENT ON CONSTRAINT chk_failure_type_consistency ON message_logs IS 
'Enforces that FAILED status always has failure_type set, and non-FAILED status always has failure_type NULL. This prevents partial updates and data inconsistencies.';






