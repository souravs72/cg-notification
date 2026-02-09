# Redis in RDS VPC for Spring Session (notification-api runs in RDS VPC and must reach Redis here)
# This fixes "logged out after ~30s" when using multiple ECS tasks: in-memory sessions are
# per-task, so requests to another task lose the session. Redis backs sessions across tasks.

# Subnet group for ElastiCache using same RDS VPC subnets as the API
resource "aws_elasticache_subnet_group" "redis_rds_vpc" {
  name       = "cg-notification-redis-rds-vpc"
  subnet_ids = data.aws_db_subnet_group.rds.subnet_ids

  tags = {
    Name = "cg-notification-redis-rds-vpc"
  }
}

# Security group for Redis in RDS VPC - allow ECS tasks (API) to connect on 6379
resource "aws_security_group" "redis_rds_vpc" {
  name        = "cg-notification-redis-sg-rds-vpc"
  description = "Redis for Spring Session in RDS VPC"
  vpc_id      = local.rds_vpc_id

  ingress {
    description     = "Redis from ECS tasks (API)"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [data.aws_security_group.ecs_in_rds_vpc.id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cg-notification-redis-rds-vpc"
  }
}

# Redis AUTH token for RDS VPC cluster (ElastiCache allows 16–128 chars; specials: !&#$^<>-)
resource "random_password" "redis_rds_vpc_auth" {
  length           = 32
  special          = true
  override_special = "!#$&^<>-"
}

resource "aws_secretsmanager_secret" "redis_password_rds_vpc" {
  name        = "cg-notification/redis-password-rds-vpc"
  description = "ElastiCache Redis AUTH token for Spring Session (RDS VPC)"

  tags = {
    Name = "cg-notification-redis-password-rds-vpc"
  }
}

resource "aws_secretsmanager_secret_version" "redis_password_rds_vpc" {
  secret_id     = aws_secretsmanager_secret.redis_password_rds_vpc.id
  secret_string = random_password.redis_rds_vpc_auth.result
}

# Single-node Redis in RDS VPC (TLS + AUTH for security)
resource "aws_elasticache_replication_group" "redis_rds_vpc" {
  replication_group_id       = "cg-notif-redis-rds"
  description                = "Redis for Spring Session (RDS VPC, API session store)"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 1
  engine                     = "redis"
  engine_version             = var.redis_engine_version
  port                       = 6379

  subnet_group_name  = aws_elasticache_subnet_group.redis_rds_vpc.name
  security_group_ids = [aws_security_group.redis_rds_vpc.id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  auth_token                 = random_password.redis_rds_vpc_auth.result

  tags = {
    Name = "cg-notification-redis-rds-vpc"
  }
}
