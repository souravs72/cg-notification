#!/bin/bash
set -e

# Run database migrations on RDS via RDS Proxy
# This script should be run after Terraform creates the RDS instance and proxy

export AWS_PROFILE=${AWS_PROFILE:-sourav-admin}
export AWS_REGION=${AWS_REGION:-ap-south-1}

RDS_PROXY_ENDPOINT=$(aws rds describe-db-proxies --db-proxy-name cg-notification-db-proxy --query 'DBProxies[0].Endpoint' --output text)
DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id cg-notification/db-password-only --query 'SecretString' --output text)
DB_USER="notification_user"
DB_NAME="notification_db"

export PGPASSWORD="$DB_PASSWORD"
export PGSSLMODE=require

echo "Running migrations on RDS Proxy: $RDS_PROXY_ENDPOINT"
echo "Waiting for connection..."
until psql -h "$RDS_PROXY_ENDPOINT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; do
  echo "Waiting for RDS Proxy to be ready..."
  sleep 5
done

echo "Connection established. Running migrations..."

MIGRATION_DIR="../deployment"
# Migration order from docker-compose.yml
MIGRATIONS=(
  "init-db.sql"
  "migration-add-users.sql"
  "migration-add-wasender-config.sql"
  "migration-add-message-fields.sql"
  "migration-add-retrying-status.sql"
  "migration-add-failure-type.sql"
  "migration-add-failure-type-constraint.sql"
  "migration-add-message-status-history.sql"
  "migration-add-history-source-column.sql"
  "migration-add-status-history-trigger.sql"
  "migration-add-email-config.sql"
  "migration-add-whatsapp-session.sql"
  "migration-make-site-id-nullable.sql"
  "migration-add-whatsapp-sessions-table.sql"
  "migration-add-sendgrid-config.sql"
  "migration-add-sendgrid-email-config.sql"
  "migration-add-partial-unique-indexes.sql"
)

for migration in "${MIGRATIONS[@]}"; do
  echo "=========================================="
  echo "Running $migration..."
  echo "=========================================="
  if psql -h "$RDS_PROXY_ENDPOINT" -U "$DB_USER" -d "$DB_NAME" -f "$MIGRATION_DIR/$migration" 2>&1; then
    echo "✅ $migration completed successfully"
  else
    echo "⚠️  $migration had errors (may already be applied)"
  fi
  echo ""
done

echo "=========================================="
echo "Migrations completed!"
echo "=========================================="
echo "Verifying tables..."
psql -h "$RDS_PROXY_ENDPOINT" -U "$DB_USER" -d "$DB_NAME" -c "\dt"
