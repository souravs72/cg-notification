# ECS Task Execution Role (for ECR pull, CloudWatch logs, Secrets Manager)
resource "aws_iam_role" "ecs_task_execution" {
  name = "cg-notification-ecs-task-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "cg-notification-ecs-task-execution-role"
  }
}

# Attach managed policy for ECS task execution
resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# CRITICAL: Add Secrets Manager permissions to execution role
# The managed policy doesn't include Secrets Manager access
# Without this, tasks fail with AccessDeniedException when trying to retrieve secrets
resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  name = "SecretsManagerAccess"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = [
          aws_secretsmanager_secret.db_password.arn,
          aws_secretsmanager_secret.db_password_only.arn,
          aws_secretsmanager_secret.redis_password.arn,
          aws_secretsmanager_secret.redis_password_rds_vpc.arn,
          aws_secretsmanager_secret.sendgrid_api_key.arn,
          aws_secretsmanager_secret.sendgrid_from_email.arn,
          aws_secretsmanager_secret.sendgrid_from_name.arn,
          aws_secretsmanager_secret.wasender_api_key.arn,
          aws_secretsmanager_secret.admin_api_key.arn
        ]
      }
    ]
  })
}

# Separate task roles for each service (security best practice - limits blast radius)

# 1. API Task Role (SNS/SQS + S3)
resource "aws_iam_role" "api_task" {
  name = "cg-api-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "cg-api-task-role"
  }
}

resource "aws_iam_role_policy" "api_task_sns_sqs_s3" {
  name = "SNS-SQS-S3-Access"
  role = aws_iam_role.api_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["sns:Publish", "sns:PublishBatch", "sns:CreateTopic", "sns:GetTopicAttributes"]
        Resource = [aws_sns_topic.email.arn, aws_sns_topic.whatsapp.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = [aws_sqs_queue.email_dlq.arn, aws_sqs_queue.whatsapp_dlq.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.uploads.arn}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
          "kms:DescribeKey"
        ]
        Resource = aws_kms_key.s3.arn
      }
    ]
  })
}

# 2. Email Worker Task Role (SQS consume)
resource "aws_iam_role" "email_worker_task" {
  name = "cg-email-worker-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "cg-email-worker-task-role"
  }
}

resource "aws_iam_role_policy" "email_worker_task_sqs" {
  name = "SQS-Access"
  role = aws_iam_role.email_worker_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Resolve queue by name (GetQueueUrl by name requires Resource "*" per AWS)
      {
        Effect   = "Allow"
        Action   = ["sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:CreateQueue",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = [aws_sqs_queue.email.arn]
      }
    ]
  })
}

# 3. WhatsApp Worker Task Role (SQS consume)
resource "aws_iam_role" "whatsapp_worker_task" {
  name = "cg-whatsapp-worker-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "cg-whatsapp-worker-task-role"
  }
}

resource "aws_iam_role_policy" "whatsapp_worker_task_sqs" {
  name = "SQS-Access"
  role = aws_iam_role.whatsapp_worker_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Resolve queue by name (GetQueueUrl by name requires Resource "*" per AWS)
      {
        Effect   = "Allow"
        Action   = ["sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:CreateQueue",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = [aws_sqs_queue.whatsapp.arn]
      }
    ]
  })
}

