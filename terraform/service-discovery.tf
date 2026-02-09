############################################
# Service discovery (Cloud Map)
# Enables ECS service-to-service DNS like:
#   whatsapp-worker.cg-notification.local
############################################

resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "cg-notification.local"
  description = "Private DNS namespace for cg-notification services"
  vpc         = aws_vpc.main.id

  tags = {
    Name = "cg-notification.local"
  }
}

resource "aws_service_discovery_service" "whatsapp_worker" {
  name = "whatsapp-worker"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  # Keep it simple: no custom health checks.
  # ECS will register/deregister task IPs automatically.

  tags = {
    Name = "whatsapp-worker.discovery"
  }
}

