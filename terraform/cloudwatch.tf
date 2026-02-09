# CloudWatch Log Groups for ECS services
resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/cg-notification-api"
  retention_in_days = var.cloudwatch_log_retention_days

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = "cg-notification-api-logs"
  }
}

resource "aws_cloudwatch_log_group" "email_worker" {
  name              = "/ecs/cg-notification-email-worker"
  retention_in_days = var.cloudwatch_log_retention_days

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = "cg-notification-email-worker-logs"
  }
}

resource "aws_cloudwatch_log_group" "whatsapp_worker" {
  name              = "/ecs/cg-notification-whatsapp-worker"
  retention_in_days = var.cloudwatch_log_retention_days

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = "cg-notification-whatsapp-worker-logs"
  }
}

