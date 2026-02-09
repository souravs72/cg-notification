# ECS Cluster (Fargate)
# Note: FARGATE_SPOT removed - not used in this deployment
# If you want to use Spot for cost savings on workers, add it back and configure per-service
resource "aws_ecs_cluster" "main" {
  name = var.ecs_cluster_name

  tags = {
    Name = var.ecs_cluster_name
  }
}




