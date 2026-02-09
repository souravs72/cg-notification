# ⚠️ WARNING: Database migrations via null_resource
#
# This approach has significant limitations:
# - Runs from operator's laptop (requires psql installed locally)
# - Breaks in CI/CD pipelines (no local psql)
# - Non-idempotent under retries (can cause issues)
# - Tightly couples infrastructure apply with schema state
# - Not suitable for multi-operator environments
#
# RECOMMENDED FOR PRODUCTION: Use ECS task-based migrations instead
# See DEPLOYMENT.md for the proper approach using run-migrations-ecs.sh
#
# This null_resource is gated by enable_db_migrations variable (default: false)
# Only enable for development/testing environments

resource "null_resource" "db_migrations" {
  count = var.enable_db_migrations ? 1 : 0
  depends_on = [
    aws_db_instance.main,
    aws_db_proxy.main,
    aws_db_proxy_target.main, # Ensure RDS instance is registered with proxy
    aws_secretsmanager_secret_version.db_password_only
  ]

  triggers = {
    # Re-run migrations if any migration file changes
    init_db                      = filemd5("${path.module}/../deployment/init-db.sql")
    migration_users              = filemd5("${path.module}/../deployment/migration-add-users.sql")
    migration_wasender           = filemd5("${path.module}/../deployment/migration-add-wasender-config.sql")
    migration_message_fields     = filemd5("${path.module}/../deployment/migration-add-message-fields.sql")
    migration_retrying           = filemd5("${path.module}/../deployment/migration-add-retrying-status.sql")
    migration_failure_type       = filemd5("${path.module}/../deployment/migration-add-failure-type.sql")
    migration_failure_constraint = filemd5("${path.module}/../deployment/migration-add-failure-type-constraint.sql")
    migration_history            = filemd5("${path.module}/../deployment/migration-add-message-status-history.sql")
    migration_history_source     = filemd5("${path.module}/../deployment/migration-add-history-source-column.sql")
    migration_trigger            = filemd5("${path.module}/../deployment/migration-add-status-history-trigger.sql")
    migration_email              = filemd5("${path.module}/../deployment/migration-add-email-config.sql")
    migration_whatsapp           = filemd5("${path.module}/../deployment/migration-add-whatsapp-session.sql")
    migration_site_nullable      = filemd5("${path.module}/../deployment/migration-make-site-id-nullable.sql")
    migration_whatsapp_table     = filemd5("${path.module}/../deployment/migration-add-whatsapp-sessions-table.sql")
    migration_sendgrid           = filemd5("${path.module}/../deployment/migration-add-sendgrid-config.sql")
    migration_sendgrid_email     = filemd5("${path.module}/../deployment/migration-add-sendgrid-email-config.sql")
    migration_indexes            = filemd5("${path.module}/../deployment/migration-add-partial-unique-indexes.sql")
    rds_endpoint                 = aws_db_instance.main.endpoint
  }

  provisioner "local-exec" {
    command = <<-EOT
      set -e
      # Use RDS Proxy endpoint for migrations (same as application)
      # Note: RDS Proxy requires TLS, so we need to add ?sslmode=require to connection
      RDS_ENDPOINT="${aws_db_proxy.main.endpoint}"
      DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id ${aws_secretsmanager_secret.db_password_only.arn} --query 'SecretString' --output text)
      
      echo "Waiting for RDS Proxy to be ready..."
      export PGPASSWORD="$DB_PASSWORD"
      export PGSSLMODE=require
      
      # Wait for RDS Proxy to accept connections (max 5 minutes)
      MAX_ATTEMPTS=60
      ATTEMPT=0
      until psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -c "SELECT 1" > /dev/null 2>&1; do
        ATTEMPT=$((ATTEMPT + 1))
        if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
          echo "ERROR: RDS Proxy not ready after $MAX_ATTEMPTS attempts"
          exit 1
        fi
        echo "Attempt $ATTEMPT/$MAX_ATTEMPTS: Waiting for RDS Proxy..."
        sleep 5
      done
      
      echo "RDS Proxy is ready. Running database migrations..."
      
      # Run migrations in correct order (matching docker-compose.yml)
      echo "Running init-db.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/init-db.sql
      echo "Running migration-add-users.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-users.sql
      echo "Running migration-add-wasender-config.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-wasender-config.sql
      echo "Running migration-add-message-fields.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-message-fields.sql
      echo "Running migration-add-retrying-status.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-retrying-status.sql
      echo "Running migration-add-failure-type.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-failure-type.sql
      echo "Running migration-add-failure-type-constraint.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-failure-type-constraint.sql
      echo "Running migration-add-message-status-history.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-message-status-history.sql
      echo "Running migration-add-history-source-column.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-history-source-column.sql
      echo "Running migration-add-status-history-trigger.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-status-history-trigger.sql
      echo "Running migration-add-email-config.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-email-config.sql
      echo "Running migration-add-whatsapp-session.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-whatsapp-session.sql
      echo "Running migration-make-site-id-nullable.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-make-site-id-nullable.sql
      echo "Running migration-add-whatsapp-sessions-table.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-whatsapp-sessions-table.sql
      echo "Running migration-add-sendgrid-config.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-sendgrid-config.sql
      echo "Running migration-add-sendgrid-email-config.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-sendgrid-email-config.sql
      echo "Running migration-add-partial-unique-indexes.sql..."
      psql -h "$RDS_ENDPOINT" -U ${var.rds_master_username} -d ${var.rds_db_name} -f ${path.module}/../deployment/migration-add-partial-unique-indexes.sql
      
      echo "Migrations completed successfully"
    EOT

    # AWS credentials are inherited from the shell environment (AWS_PROFILE, AWS_REGION)
  }
}
