# Security group for ECS tasks
resource "aws_security_group" "ecs" {
  name        = "cg-notification-ecs-sg"
  description = "Security group for cg-notification ECS tasks"
  vpc_id      = aws_vpc.main.id

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-ecs-sg"
  }
}

# Allow ECS tasks to call other ECS tasks (service-to-service) on worker ports.
# Without this, API -> whatsapp-worker calls will timeout/refuse even if DNS resolves.
resource "aws_security_group_rule" "ecs_to_ecs_whatsapp_worker" {
  type                     = "ingress"
  from_port                = 8082
  to_port                  = 8082
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ecs.id
  security_group_id        = aws_security_group.ecs.id
  description              = "Allow ECS-to-ECS traffic to whatsapp-worker port"
}


# Security group for Redis (ElastiCache) used by Spring Session
resource "aws_security_group" "redis" {
  name        = "cg-notification-redis-sg"
  description = "Security group for cg-notification Redis (Spring Session)"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Redis from ECS tasks"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-redis-sg"
  }
}

# Security group for VPC Interface Endpoints (Secrets Manager, ECR, Logs)
# Interface endpoints have ENIs; this SG must allow HTTPS from ECS tasks.
resource "aws_security_group" "vpc_endpoints" {
  name        = "cg-notification-vpc-endpoints-sg"
  description = "Security group for cg-notification VPC interface endpoints"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "HTTPS from ECS tasks"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-vpc-endpoints-sg"
  }
}

# Security group for ALB
resource "aws_security_group" "alb" {
  name        = "cg-notification-alb-sg"
  description = "Security group for cg-notification ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-alb-sg"
  }
}

# Security group for RDS
resource "aws_security_group" "rds" {
  name        = "cg-notification-rds-sg"
  description = "Security group for cg-notification RDS"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  # Allow RDS Proxy to connect to RDS instance
  ingress {
    description     = "PostgreSQL from RDS Proxy"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.rds_proxy.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-rds-sg"
  }
}

# Security group for RDS Proxy
resource "aws_security_group" "rds_proxy" {
  name        = "cg-notification-rds-proxy-sg"
  description = "Security group for cg-notification RDS Proxy"
  vpc_id      = aws_vpc.main.id

  # Allow ECS tasks to connect to RDS Proxy
  ingress {
    description     = "PostgreSQL from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-rds-proxy-sg"
  }
}

# Security group for MSK
resource "aws_security_group" "msk" {
  name        = "cg-notification-msk-sg"
  description = "Security group for cg-notification MSK"
  vpc_id      = aws_vpc.main.id

  # MSK Serverless with IAM uses port 9098 for client connections
  ingress {
    description     = "Kafka IAM auth from ECS tasks"
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  # CRITICAL: Ephemeral ports required for client ↔ broker connections (NOT internode)
  # MSK Serverless brokers are managed by AWS; internode traffic is AWS-managed
  # Required for: Kafka clients open multiple outbound TCP connections during rebalancing,
  # fetch/produce pipelines, and connection churn
  # Without this, consumers will randomly fail during rebalancing
  ingress {
    description     = "Kafka ephemeral ports from ECS tasks (client-broker connections)"
    from_port       = 1024
    to_port         = 65535
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-msk-sg"
  }
}

# Allow ALB to access ECS tasks on port 8080
resource "aws_security_group_rule" "ecs_from_alb" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb.id
  security_group_id        = aws_security_group.ecs.id
  description              = "Allow ALB to access ECS tasks"
}




