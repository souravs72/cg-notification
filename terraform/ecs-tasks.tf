# Local values for common configuration
locals {
  rds_endpoint       = aws_db_instance.main.endpoint
  rds_proxy_endpoint = aws_db_proxy.main.endpoint
  aws_region         = var.aws_region
  account_id         = data.aws_caller_identity.current.account_id
}

# Task Definition for notification-api
resource "aws_ecs_task_definition" "api" {
  family                   = "cg-notification-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "1024"
  memory                   = "4096"  # 4GB - sufficient for API + SNS/SQS and retry batches
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.api_task.arn

  container_definitions = jsonencode([
    {
      name      = "notification-api"
      image     = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${local.rds_proxy_endpoint}:5432/${var.rds_db_name}?sslmode=require"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = var.rds_master_username
        },
        {
          name  = "AWS_REGION"
          value = local.aws_region
        },
        # Use topic ARN so SNS client does not call CreateTopic (avoids IAM CreateTopic requirement)
        {
          name  = "MESSAGING_SNS_TOPIC_EMAIL"
          value = aws_sns_topic.email.arn
        },
        {
          name  = "MESSAGING_SNS_TOPIC_WHATSAPP"
          value = aws_sns_topic.whatsapp.arn
        },
        {
          name  = "MESSAGING_SQS_QUEUE_EMAIL_DLQ"
          value = aws_sqs_queue.email_dlq.name
        },
        {
          name  = "MESSAGING_SQS_QUEUE_WHATSAPP_DLQ"
          value = aws_sqs_queue.whatsapp_dlq.name
        },
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "prod"
        },
        # Disable tracing (no Zipkin in ECS - avoids Connection refused localhost:9411)
        {
          name  = "MANAGEMENT_TRACING_ENABLED"
          value = "false"
        },
        # whatsapp-worker API base URL (internal, via Cloud Map service discovery)
        {
          name  = "WHATSAPP_WORKER_API_BASE_URL"
          value = "http://whatsapp-worker.cg-notification.local:8082"
        },
        # Spring Session: use none (in-memory) - API runs in RDS VPC, Redis in Terraform VPC (no connectivity).
        # ALB stickiness keeps users on same task so sessions work.
        {
          name  = "SPRING_SESSION_STORE_TYPE"
          value = "none"
        },
        # Disable Redis autoconfig - avoids connection attempts when Redis unreachable
        {
          name  = "SPRING_AUTOCONFIGURE_EXCLUDE"
          value = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
        },
        {
          name  = "FILE_UPLOAD_DIR"
          value = "/app/uploads"
        },
        {
          name  = "FILE_UPLOAD_BASE_URL"
          value = var.file_upload_base_url != "" ? var.file_upload_base_url : "https://${aws_lb.main.dns_name}/files"
        }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = aws_secretsmanager_secret.db_password_only.arn
        },
        {
          name      = "ADMIN_API_KEY"
          valueFrom = aws_secretsmanager_secret.admin_api_key.arn
        },
        {
          name      = "WASENDER_API_KEY"
          valueFrom = aws_secretsmanager_secret.wasender_api_key.arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.api.name
          "awslogs-region"        = local.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      # NOTE: Container health check removed - distroless image doesn't have curl
      # ALB target group health check handles health monitoring at /actuator/health
      # This is sufficient for Spring Boot applications
    }
  ])

  tags = {
    Name = "cg-notification-api"
  }
}

# Task Definition for email-worker
resource "aws_ecs_task_definition" "email_worker" {
  family                   = "cg-notification-email-worker"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.email_worker_task.arn

  container_definitions = jsonencode([
    {
      name      = "email-worker"
      image     = "${aws_ecr_repository.email_worker.repository_url}:${var.image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = 8081
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${local.rds_proxy_endpoint}:5432/${var.rds_db_name}?sslmode=require"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = var.rds_master_username
        },
        {
          name  = "AWS_REGION"
          value = local.aws_region
        },
        {
          name  = "MESSAGING_SQS_QUEUE_EMAIL"
          value = aws_sqs_queue.email.name
        },
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "prod"
        },
        {
          name  = "SERVER_PORT"
          value = "8081"
        }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = aws_secretsmanager_secret.db_password_only.arn
        },
        {
          name      = "SENDGRID_API_KEY"
          valueFrom = aws_secretsmanager_secret.sendgrid_api_key.arn
        },
        {
          name      = "SENDGRID_FROM_EMAIL"
          valueFrom = aws_secretsmanager_secret.sendgrid_from_email.arn
        },
        {
          name      = "SENDGRID_FROM_NAME"
          valueFrom = aws_secretsmanager_secret.sendgrid_from_name.arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.email_worker.name
          "awslogs-region"        = local.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      # NOTE: Container health check removed - distroless image doesn't have curl
      # Workers don't have ALB, so health monitoring relies on ECS service health checks
      # and CloudWatch logs monitoring
    }
  ])

  tags = {
    Name = "cg-notification-email-worker"
  }
}

# Task Definition for whatsapp-worker
resource "aws_ecs_task_definition" "whatsapp_worker" {
  family                   = "cg-notification-whatsapp-worker"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.whatsapp_worker_task.arn

  container_definitions = jsonencode([
    {
      name      = "whatsapp-worker"
      image     = "${aws_ecr_repository.whatsapp_worker.repository_url}:${var.image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = 8082
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${local.rds_proxy_endpoint}:5432/${var.rds_db_name}?sslmode=require"
        },
        {
          name  = "SPRING_DATASOURCE_USERNAME"
          value = var.rds_master_username
        },
        {
          name  = "AWS_REGION"
          value = local.aws_region
        },
        {
          name  = "MESSAGING_SQS_QUEUE_WHATSAPP"
          value = aws_sqs_queue.whatsapp.name
        },
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = "prod"
        },
        {
          name  = "SERVER_PORT"
          value = "8082"
        }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = aws_secretsmanager_secret.db_password_only.arn
        },
        {
          name      = "WASENDER_API_KEY"
          valueFrom = aws_secretsmanager_secret.wasender_api_key.arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.whatsapp_worker.name
          "awslogs-region"        = local.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      # NOTE: Container health check removed - distroless image doesn't have curl
      # Workers don't have ALB, so health monitoring relies on ECS service health checks
      # and CloudWatch logs monitoring
    }
  ])

  tags = {
    Name = "cg-notification-whatsapp-worker"
  }
}




