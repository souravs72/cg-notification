# Database Migration Order Analysis

## Current State

### Migrations in docker-compose.yml (Current Order)
1. `00-init.sql` - Base schema (init-db.sql)
2. `01-whatsapp.sql` - migration-add-whatsapp-session.sql
3. `02-email.sql` - migration-add-email-config.sql
4. `03-users.sql` - migration-add-users.sql
5. `04-site.sql` - migration-make-site-id-nullable.sql
6. `05-whatsapp-table.sql` - migration-add-whatsapp-sessions-table.sql
7. `06-sendgrid.sql` - migration-add-sendgrid-config.sql
8. `07-sendgrid-email.sql` - migration-add-sendgrid-email-config.sql

### Missing Migrations (Not in docker-compose.yml)
- `migration-add-message-fields.sql`
- `migration-add-retrying-status.sql`
- `migration-add-failure-type.sql`
- `migration-add-failure-type-constraint.sql`
- `migration-add-message-status-history.sql`
- `migration-add-history-source-column.sql`
- `migration-add-status-history-trigger.sql`
- `migration-add-partial-unique-indexes.sql`

## Dependency Analysis

### Dependencies Identified

1. **init-db.sql** (00-init.sql)
   - Creates base tables: `frappe_sites`, `message_logs`, `site_metrics_daily`
   - Creates enums: `delivery_status`, `notification_channel`
   - Creates function: `update_updated_at_column()`
   - **No dependencies** ✓

2. **migration-add-users.sql** (03-users.sql)
   - Creates `users` table
   - Uses `update_updated_at_column()` function from init-db.sql
   - **Depends on:** init-db.sql ✓

3. **migration-add-message-fields.sql** (MISSING)
   - Adds columns to `message_logs` table
   - Adds `SCHEDULED` to `delivery_status` enum
   - **Depends on:** init-db.sql (message_logs table exists)
   - **Should run:** After init-db.sql, before any enum-dependent migrations

4. **migration-add-retrying-status.sql** (MISSING)
   - Adds `RETRYING` to `delivery_status` enum
   - **Depends on:** init-db.sql (delivery_status enum exists)
   - **Should run:** After init-db.sql

5. **migration-add-failure-type.sql** (MISSING)
   - Creates `failure_type` enum
   - Adds `failure_type` column to `message_logs`
   - Backfills existing data
   - **Depends on:** init-db.sql (message_logs table exists)
   - **Should run:** After init-db.sql, before constraint migration

6. **migration-add-failure-type-constraint.sql** (MISSING)
   - Adds constraint on `failure_type` column
   - **Depends on:** migration-add-failure-type.sql (explicitly mentioned in comments)
   - **Should run:** After migration-add-failure-type.sql ✓

7. **migration-add-message-status-history.sql** (MISSING)
   - Creates `message_status_history` table
   - Uses `delivery_status` enum
   - **Depends on:** init-db.sql (delivery_status enum exists)
   - **Should run:** After init-db.sql

8. **migration-add-history-source-column.sql** (MISSING)
   - Creates `history_source` enum
   - Adds `source` column to `message_status_history`
   - **Depends on:** migration-add-message-status-history.sql (table must exist)
   - **Should run:** After migration-add-message-status-history.sql ✓

9. **migration-add-status-history-trigger.sql** (MISSING)
   - Creates trigger function and trigger
   - Uses `history_source` enum and `source` column
   - **Depends on:** migration-add-history-source-column.sql (explicitly mentioned in comments: "⚠️ PREREQUISITE")
   - **Should run:** After migration-add-history-source-column.sql ✓

10. **migration-add-email-config.sql** (02-email.sql)
    - Adds columns to `frappe_sites` table
    - **Depends on:** init-db.sql (frappe_sites table exists) ✓

11. **migration-add-whatsapp-session.sql** (01-whatsapp.sql)
    - Adds column to `frappe_sites` table
    - **Depends on:** init-db.sql (frappe_sites table exists) ✓

12. **migration-make-site-id-nullable.sql** (04-site.sql)
    - Modifies `message_logs` table
    - **Depends on:** init-db.sql (message_logs table exists) ✓

13. **migration-add-whatsapp-sessions-table.sql** (05-whatsapp-table.sql)
    - Creates `whatsapp_sessions` table
    - References `users` table (FK constraint)
    - **Depends on:** migration-add-users.sql ✓

14. **migration-add-sendgrid-config.sql** (06-sendgrid.sql)
    - Creates `sendgrid_config` table
    - **No dependencies** ✓

15. **migration-add-sendgrid-email-config.sql** (07-sendgrid-email.sql)
    - Adds columns to `sendgrid_config` table
    - **Depends on:** migration-add-sendgrid-config.sql ✓

16. **migration-add-partial-unique-indexes.sql** (MISSING)
    - Creates indexes on `frappe_sites`, `users`, `whatsapp_sessions`
    - Drops existing constraint on `whatsapp_sessions`
    - **Depends on:** 
      - init-db.sql (frappe_sites exists)
      - migration-add-users.sql (users exists)
      - migration-add-whatsapp-sessions-table.sql (whatsapp_sessions exists)
    - **Should run:** After all table-creating migrations ✓

## Correct Migration Order

### Recommended Order (with numbering)

1. **00-init.sql** - Base schema
2. **01-users.sql** - Create users table (needed for whatsapp_sessions FK)
3. **02-message-fields.sql** - Add message fields and SCHEDULED enum
4. **03-retrying-status.sql** - Add RETRYING enum value
5. **04-failure-type.sql** - Create failure_type enum and column
6. **05-failure-type-constraint.sql** - Add constraint (requires backfilled data)
7. **06-message-status-history.sql** - Create history table
8. **07-history-source-column.sql** - Add source column to history
9. **08-status-history-trigger.sql** - Create trigger (requires source column)
10. **09-email-config.sql** - Add email config to frappe_sites
11. **10-whatsapp-session.sql** - Add whatsapp session to frappe_sites
12. **11-site-nullable.sql** - Make site_id nullable
13. **12-whatsapp-sessions-table.sql** - Create whatsapp_sessions (requires users)
14. **13-sendgrid-config.sql** - Create sendgrid_config
15. **14-sendgrid-email-config.sql** - Add email config to sendgrid_config
16. **15-partial-unique-indexes.sql** - Create indexes (requires all tables)

## Issues Found

### ❌ Critical Issues

1. **Missing migrations in docker-compose.yml:**
   - 8 migrations are not included in docker-compose.yml
   - These will not run automatically on fresh database initialization

2. **Incorrect order in docker-compose.yml:**
   - `01-whatsapp.sql` runs before `03-users.sql`, but `05-whatsapp-table.sql` depends on users
   - This is actually OK since `01-whatsapp.sql` only adds a column to `frappe_sites`, not the table
   - However, `05-whatsapp-table.sql` correctly runs after `03-users.sql` ✓

3. **Enum additions order:**
   - `migration-add-message-fields.sql` adds `SCHEDULED` to enum
   - `migration-add-retrying-status.sql` adds `RETRYING` to enum
   - Both should run early, after init-db.sql
   - **Current:** Both missing from docker-compose.yml ❌

4. **Failure type constraint:**
   - `migration-add-failure-type-constraint.sql` must run AFTER `migration-add-failure-type.sql`
   - This is because the constraint migration requires data to be backfilled first
   - **Current:** Both missing from docker-compose.yml ❌

5. **Status history trigger:**
   - `migration-add-status-history-trigger.sql` explicitly requires `migration-add-history-source-column.sql` first
   - The trigger uses the `source` column and `history_source` enum
   - **Current:** Both missing from docker-compose.yml ❌

6. **Partial unique indexes:**
   - Must run after all tables are created
   - Drops constraint on `whatsapp_sessions`, so must run after `migration-add-whatsapp-sessions-table.sql`
   - **Current:** Missing from docker-compose.yml ❌

## Recommendations

### ✅ Fix docker-compose.yml

Add all missing migrations in the correct order:

```yaml
volumes:
  - postgres_data:/var/lib/postgresql/data
  - ./deployment/init-db.sql:/docker-entrypoint-initdb.d/00-init.sql
  - ./deployment/migration-add-users.sql:/docker-entrypoint-initdb.d/01-users.sql
  - ./deployment/migration-add-message-fields.sql:/docker-entrypoint-initdb.d/02-message-fields.sql
  - ./deployment/migration-add-retrying-status.sql:/docker-entrypoint-initdb.d/03-retrying-status.sql
  - ./deployment/migration-add-failure-type.sql:/docker-entrypoint-initdb.d/04-failure-type.sql
  - ./deployment/migration-add-failure-type-constraint.sql:/docker-entrypoint-initdb.d/05-failure-type-constraint.sql
  - ./deployment/migration-add-message-status-history.sql:/docker-entrypoint-initdb.d/06-message-status-history.sql
  - ./deployment/migration-add-history-source-column.sql:/docker-entrypoint-initdb.d/07-history-source-column.sql
  - ./deployment/migration-add-status-history-trigger.sql:/docker-entrypoint-initdb.d/08-status-history-trigger.sql
  - ./deployment/migration-add-email-config.sql:/docker-entrypoint-initdb.d/09-email-config.sql
  - ./deployment/migration-add-whatsapp-session.sql:/docker-entrypoint-initdb.d/10-whatsapp-session.sql
  - ./deployment/migration-make-site-id-nullable.sql:/docker-entrypoint-initdb.d/11-site-nullable.sql
  - ./deployment/migration-add-whatsapp-sessions-table.sql:/docker-entrypoint-initdb.d/12-whatsapp-sessions-table.sql
  - ./deployment/migration-add-sendgrid-config.sql:/docker-entrypoint-initdb.d/13-sendgrid-config.sql
  - ./deployment/migration-add-sendgrid-email-config.sql:/docker-entrypoint-initdb.d/14-sendgrid-email-config.sql
  - ./deployment/migration-add-partial-unique-indexes.sql:/docker-entrypoint-initdb.d/15-partial-unique-indexes.sql
```

## Summary

**Status:** ❌ **Migrations will NOT run in correct order**

**Problems:**
1. 8 migrations are missing from docker-compose.yml
2. Critical dependencies are not satisfied:
   - Failure type constraint requires failure type migration first
   - Status history trigger requires history source column first
   - Partial indexes require all tables to exist first

**Action Required:**
- Update docker-compose.yml to include all migrations in the correct order
- Test migration order on fresh database


