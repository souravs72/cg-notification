# KMS Key for RDS encryption (customer-managed key for production compliance)
resource "aws_kms_key" "rds" {
  description             = "RDS encryption key for cg-notification-db"
  deletion_window_in_days = 10

  # Explicit key policy for production: allows root account and RDS service
  # RDS service needs permissions to use the key for encryption/decryption
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "Enable IAM User Permissions"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "Allow RDS Service"
        Effect = "Allow"
        Principal = {
          Service = "rds.amazonaws.com"
        }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:CreateGrant",
          "kms:DescribeKey"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "kms:ViaService" = [
              "rds.${var.aws_region}.amazonaws.com"
            ]
          }
        }
      }
    ]
  })

  tags = {
    Name = "cg-notification-rds-key"
  }
}

resource "aws_kms_alias" "rds" {
  name          = "alias/cg-notification-rds"
  target_key_id = aws_kms_key.rds.key_id
}

# DB Subnet Group for RDS
resource "aws_db_subnet_group" "main" {
  name       = "cg-notification-db-subnet"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "cg-notification-db-subnet"
  }

  lifecycle {
    # Avoid update errors when existing subnet group uses different VPC subnets
    ignore_changes = [subnet_ids]
  }
}

# RDS PostgreSQL Instance (Multi-AZ)
resource "aws_db_instance" "main" {
  identifier             = "cg-notification-db"
  engine                 = "postgres"
  engine_version         = var.rds_engine_version
  instance_class         = var.rds_instance_class
  allocated_storage      = var.rds_allocated_storage
  storage_type           = "gp3"
  storage_encrypted      = true
  kms_key_id             = aws_kms_key.rds.arn # Use customer-managed KMS key for RDS encryption
  multi_az               = true
  publicly_accessible    = false
  db_name                = var.rds_db_name
  username               = var.rds_master_username
  password               = random_password.db_password.result
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id] # Use RDS security group which allows both ECS and RDS Proxy

  # Enable IAM database authentication (required for RDS Proxy)
  iam_database_authentication_enabled = true

  backup_retention_period   = 7
  skip_final_snapshot       = false
  final_snapshot_identifier = "cg-notification-db-final-snapshot"
  deletion_protection       = true # CRITICAL: Prevent accidental deletion in production

  tags = {
    Name = "cg-notification-db"
  }

  lifecycle {
    prevent_destroy = true
    # Imported instance: avoid overwriting; RDS is in different VPC than Terraform-managed SGs
    ignore_changes = [password, kms_key_id, vpc_security_group_ids]
  }
}

# IAM Role for RDS Proxy
resource "aws_iam_role" "rds_proxy" {
  name = "cg-notification-rds-proxy-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "rds.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "cg-notification-rds-proxy-role"
  }
}

# Minimal Secrets Manager permissions for RDS Proxy
resource "aws_iam_role_policy" "rds_proxy_secrets" {
  name = "SecretsManagerAccess"
  role = aws_iam_role.rds_proxy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = aws_secretsmanager_secret.db_password.arn
      }
    ]
  })
}

# RDS Proxy (manages connection pooling and reduces connection pressure)
# MUST be in same VPC as RDS and ECS - uses rds-proxy-rds-vpc.tf data sources
resource "aws_db_proxy" "main" {
  name          = "cg-notification-db-proxy"
  engine_family = "POSTGRESQL"
  auth {
    auth_scheme = "SECRETS"
    secret_arn  = aws_secretsmanager_secret.db_password.arn
  }
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_subnet_ids         = data.aws_db_subnet_group.rds.subnet_ids
  vpc_security_group_ids = [aws_security_group.rds_proxy_in_rds_vpc.id]

  require_tls = true

  depends_on = [
    aws_iam_role.rds_proxy,
    aws_iam_role_policy.rds_proxy_secrets,
    aws_secretsmanager_secret_version.db_password,
    aws_security_group.rds_proxy_in_rds_vpc
  ]

  tags = {
    Name = "cg-notification-db-proxy"
  }
}

# Register RDS instance with proxy
# Explicit depends_on ensures RDS instance is fully available before registration
resource "aws_db_proxy_target" "main" {
  db_proxy_name          = aws_db_proxy.main.name
  target_group_name      = "default"
  db_instance_identifier = aws_db_instance.main.identifier

  # Ensure RDS instance is fully available and RDS Proxy is ready before registration
  depends_on = [
    aws_db_instance.main,
    aws_db_proxy.main,
    aws_secretsmanager_secret_version.db_password # Ensure secret is available for proxy auth
  ]
}

