# S3 Bucket for file uploads (stateful: user data)
resource "aws_s3_bucket" "uploads" {
  bucket = var.s3_bucket_name != "" ? var.s3_bucket_name : "cg-notification-uploads-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "cg-notification-uploads"
  }

  lifecycle {
    prevent_destroy = true
  }
}

# Enable versioning on S3 bucket
resource "aws_s3_bucket_versioning" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Lifecycle policy to delete old versions after 90 days
resource "aws_s3_bucket_lifecycle_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    id     = "DeleteOldVersions"
    status = "Enabled"

    filter {
      prefix = ""
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

# KMS key for S3 encryption
resource "aws_kms_key" "s3" {
  description             = "S3 encryption key for cg-notification-uploads"
  deletion_window_in_days = 10

  # Explicit key policy for production: allows root account and ECS task role
  # Default policy allows root, but explicit policy is clearer for audits
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
        Sid    = "Allow ECS API Task Role"
        Effect = "Allow"
        Principal = {
          AWS = aws_iam_role.api_task.arn
        }
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
          "kms:DescribeKey"
        ]
        Resource = "*"
      }
    ]
  })

  tags = {
    Name = "cg-notification-s3-key"
  }
}

resource "aws_kms_alias" "s3" {
  name          = "alias/cg-notification-s3"
  target_key_id = aws_kms_key.s3.key_id
}

# CRITICAL: Enable SSE-KMS encryption (not SSE-S3)
resource "aws_s3_bucket_server_side_encryption_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
    bucket_key_enabled = true
  }
}

# CRITICAL: Block all public access explicitly
resource "aws_s3_bucket_public_access_block" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# Bucket policy - only ECS API task role should have access
resource "aws_s3_bucket_policy" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowECSAPITaskRole"
        Effect = "Allow"
        Principal = {
          AWS = aws_iam_role.api_task.arn
        }
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.uploads.arn}/*"
      }
    ]
  })
}

# Data source for current AWS account ID
data "aws_caller_identity" "current" {}




