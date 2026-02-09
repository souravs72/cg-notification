# ECS Service for notification-api
resource "aws_ecs_service" "api" {
  name            = "notification-api-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.notification_api_desired_count
  launch_type     = "FARGATE"

  # API runs in RDS VPC to reach RDS Proxy; ALB/target group are also in RDS VPC
  network_configuration {
    subnets          = tolist(data.aws_db_subnet_group.rds.subnet_ids)
    security_groups  = [data.aws_security_group.ecs_in_rds_vpc.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "notification-api"
    container_port   = 8080
  }

  health_check_grace_period_seconds = 300
  enable_execute_command            = true

  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  depends_on = [
    aws_lb_listener.http,
    null_resource.alb_listeners,
    aws_db_instance.main,
  ]

  tags = {
    Name = "notification-api-service"
  }
}

# ECS Service for email-worker
resource "aws_ecs_service" "email_worker" {
  name            = "email-worker-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.email_worker.arn
  desired_count   = var.email_worker_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  enable_execute_command = true # Enable ECS Exec for live debugging and emergency inspection

  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  depends_on = [
    aws_db_instance.main,
  ]

  tags = {
    Name = "email-worker-service"
  }

  lifecycle {
    ignore_changes = [network_configuration]
  }
}

# ECS Service for whatsapp-worker
resource "aws_ecs_service" "whatsapp_worker" {
  name            = "whatsapp-worker-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.whatsapp_worker.arn
  desired_count   = var.whatsapp_worker_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  enable_execute_command = true # Enable ECS Exec for live debugging and emergency inspection

  # Service discovery so other services can call:
  #   http://whatsapp-worker.cg-notification.local:8082
  service_registries {
    registry_arn = aws_service_discovery_service.whatsapp_worker.arn
  }

  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  depends_on = [
    aws_db_instance.main,
  ]

  tags = {
    Name = "whatsapp-worker-service"
  }

  lifecycle {
    # Preserve network_configuration and service_registries (registry may differ across VPCs)
    ignore_changes = [network_configuration, service_registries]
  }
}
