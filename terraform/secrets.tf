# Random password for RDS
# CRITICAL: RDS password must not contain: '/', '@', '"', ' ' (space)
# Use override_special to exclude these characters
resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?" # Exclude: /, @, ", space
}

# Database password secret
resource "aws_secretsmanager_secret" "db_password" {
  name        = "cg-notification/db-password"
  description = "RDS PostgreSQL password for cg-notification"

  tags = {
    Name = "cg-notification-db-password"
  }
}

# CRITICAL: RDS Proxy expects JSON format, not raw string
# Format: {"username": "...", "password": "..."}
resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id = aws_secretsmanager_secret.db_password.id
  secret_string = jsonencode({
    username = var.rds_master_username
    password = random_password.db_password.result
  })
}

# Separate password-only secret for Spring Boot applications
# Spring Boot expects SPRING_DATASOURCE_PASSWORD to be a plain string, not JSON
# RDS Proxy uses the JSON format secret above
resource "aws_secretsmanager_secret" "db_password_only" {
  name        = "cg-notification/db-password-only"
  description = "RDS PostgreSQL password (plain string) for Spring Boot applications"

  tags = {
    Name = "cg-notification-db-password-only"
  }
}

resource "aws_secretsmanager_secret_version" "db_password_only" {
  secret_id     = aws_secretsmanager_secret.db_password_only.id
  secret_string = random_password.db_password.result
}

# Redis AUTH token (ElastiCache). 16–128 chars; allowed specials: !&#$^<>- only
# TLS encrypts transit; auth token adds authentication.
resource "random_password" "redis_auth" {
  length           = 32
  special          = true
  override_special = "!#$&^<>-"
}

resource "aws_secretsmanager_secret" "redis_password" {
  name        = "cg-notification/redis-password"
  description = "ElastiCache Redis AUTH token for Spring Session"

  tags = {
    Name = "cg-notification-redis-password"
  }
}

resource "aws_secretsmanager_secret_version" "redis_password" {
  secret_id     = aws_secretsmanager_secret.redis_password.id
  secret_string = random_password.redis_auth.result
}

# SendGrid API key secret
# SECURITY: Secret value is NOT managed by Terraform to prevent:
# - Secrets leaking into Terraform state
# - Accidental exposure in version control
# - AWS security findings for hardcoded credentials
# Secret value must be created manually via AWS Console, CI/CD, or AWS CLI before first deployment
# ⚠️ WARNING: Terraform apply will fail if secret value is not created manually
resource "aws_secretsmanager_secret" "sendgrid_api_key" {
  name        = "cg-notification/sendgrid-api-key"
  description = "SendGrid API key"

  tags = {
    Name = "cg-notification-sendgrid-api-key"
  }
}

# SendGrid from email secret
# SECURITY: Secret value is NOT managed by Terraform to prevent:
# - Secrets leaking into Terraform state
# - Accidental exposure in version control
# - AWS security findings for hardcoded credentials
# Secret value must be created manually via AWS Console, CI/CD, or AWS CLI before first deployment
# ⚠️ WARNING: Terraform apply will fail if secret value is not created manually
resource "aws_secretsmanager_secret" "sendgrid_from_email" {
  name        = "cg-notification/sendgrid-from-email"
  description = "SendGrid from email address"

  tags = {
    Name = "cg-notification-sendgrid-from-email"
  }
}

# SendGrid from name secret
# SECURITY: Secret value is NOT managed by Terraform to prevent:
# - Secrets leaking into Terraform state
# - Accidental exposure in version control
# - AWS security findings for hardcoded credentials
# Secret value must be created manually via AWS Console, CI/CD, or AWS CLI before first deployment
# ⚠️ WARNING: Terraform apply will fail if secret value is not created manually
resource "aws_secretsmanager_secret" "sendgrid_from_name" {
  name        = "cg-notification/sendgrid-from-name"
  description = "SendGrid from name"

  tags = {
    Name = "cg-notification-sendgrid-from-name"
  }
}

# WASender API key secret
# SECURITY: Secret value is NOT managed by Terraform to prevent:
# - Secrets leaking into Terraform state
# - Accidental exposure in version control
# - AWS security findings for hardcoded credentials
# Secret value must be created manually via AWS Console, CI/CD, or AWS CLI before first deployment
# ⚠️ WARNING: Terraform apply will fail if secret value is not created manually
resource "aws_secretsmanager_secret" "wasender_api_key" {
  name        = "cg-notification/wasender-api-key"
  description = "WASender API key"

  tags = {
    Name = "cg-notification-wasender-api-key"
  }
}

# Admin API key secret (for notification-api)
# SECURITY: Secret value is NOT managed by Terraform to prevent:
# - Secrets leaking into Terraform state
# - Accidental exposure in version control
# - AWS security findings for hardcoded credentials
# Secret value must be created manually via AWS Console, CI/CD, or AWS CLI before first deployment
# ⚠️ WARNING: Terraform apply will fail if secret value is not created manually
resource "aws_secretsmanager_secret" "admin_api_key" {
  name        = "cg-notification/admin-api-key"
  description = "Admin API key for notification-api"

  tags = {
    Name = "cg-notification-admin-api-key"
  }
}

