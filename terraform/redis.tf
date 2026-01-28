############################################
# Redis (Spring Session backing store)
############################################

# Subnet group for ElastiCache in private subnets
resource "aws_elasticache_subnet_group" "redis" {
  name       = "cg-notification-redis-subnets"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "cg-notification-redis-subnets"
  }
}

# Single-node Redis replication group. Transit encryption (TLS) requires replication group, not cache cluster.
# auth_token (TLS != auth): enables AUTH so only clients with the token can connect.
# App: spring.data.redis.ssl=true, spring.data.redis.password from SPRING_DATA_REDIS_PASSWORD (Secrets Manager).
# If this group already exists without auth, adding auth_token forces replace. Temporarily remove the
# lifecycle { prevent_destroy } block, apply, then add it back.
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "cg-notification-redis"
  description                = "Redis for Spring Session (single node, TLS + AUTH)"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 1
  engine                     = "redis"
  engine_version             = var.redis_engine_version
  port                       = 6379

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  auth_token                 = random_password.redis_auth.result

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = "cg-notification-redis"
  }
}
