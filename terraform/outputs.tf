output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer"
  value       = aws_lb.main.dns_name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = aws_db_instance.main.endpoint
  sensitive   = false
}

output "rds_proxy_endpoint" {
  description = "RDS Proxy endpoint (use this instead of RDS endpoint in ECS tasks)"
  value       = aws_db_proxy.main.endpoint
  sensitive   = false
}

output "rds_address" {
  description = "RDS PostgreSQL address (hostname only)"
  value       = aws_db_instance.main.address
}

output "msk_bootstrap_brokers" {
  description = "MSK Serverless bootstrap brokers (SASL/IAM)"
  value       = aws_msk_serverless_cluster.main.bootstrap_brokers_sasl_iam
  sensitive   = true
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "ecs_security_group_id" {
  description = "ECS security group ID (Terraform VPC)"
  value       = aws_security_group.ecs.id
}

output "migration_subnet_ids" {
  description = "Subnet IDs for migration task (RDS VPC - where RDS Proxy is reachable)"
  value       = data.aws_db_subnet_group.rds.subnet_ids
}

output "migration_security_group_id" {
  description = "Security group for migration task (ECS SG in RDS VPC - allowed by RDS Proxy)"
  value       = data.aws_security_group.ecs_in_rds_vpc.id
}

output "ecr_api_repository_url" {
  description = "ECR repository URL for notification-api"
  value       = aws_ecr_repository.api.repository_url
}

output "ecr_email_worker_repository_url" {
  description = "ECR repository URL for email-worker"
  value       = aws_ecr_repository.email_worker.repository_url
}

output "ecr_whatsapp_worker_repository_url" {
  description = "ECR repository URL for whatsapp-worker"
  value       = aws_ecr_repository.whatsapp_worker.repository_url
}

output "ecr_kafka_admin_repository_url" {
  description = "ECR repository URL for kafka-admin (MSK topic creation)"
  value       = aws_ecr_repository.kafka_admin.repository_url
}

output "s3_bucket_name" {
  description = "S3 bucket name for uploads"
  value       = aws_s3_bucket.uploads.id
}

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs (for ECS tasks and RDS)"
  value       = aws_subnet.private[*].id
}

output "public_subnet_ids" {
  description = "Public subnet IDs (for ALB and NAT Gateway)"
  value       = aws_subnet.public[*].id
}

output "redis_endpoint" {
  description = "Redis endpoint for Spring Session (hostname)"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "image_tag" {
  description = "Docker image tag used in ECS task definitions (var.image_tag)"
  value       = var.image_tag
}

output "domain_name" {
  description = "Custom domain name (if provided)"
  value       = var.domain_name != "" ? var.domain_name : null
}

output "acm_certificate_arn" {
  description = "ACM certificate ARN (created if domain_name is provided)"
  value       = var.domain_name != "" ? aws_acm_certificate.main[0].arn : (var.acm_certificate_arn != "" ? var.acm_certificate_arn : null)
}

output "route53_zone_id" {
  description = "Route53 zone ID (if provided)"
  value       = var.route53_zone_id != "" ? var.route53_zone_id : null
}

output "acm_certificate_validation_records" {
  description = "DNS validation records for ACM certificate (if domain_name is provided and route53_zone_id is NOT provided - for manual DNS setup)"
  value = var.domain_name != "" && var.route53_zone_id == "" ? {
    for dvo in aws_acm_certificate.main[0].domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  } : null
}

output "application_url" {
  description = "Application URL (domain if provided, otherwise ALB DNS)"
  value       = var.domain_name != "" && var.route53_zone_id != "" ? "https://${var.domain_name}" : "http://${aws_lb.main.dns_name}"
}

output "alb_url" {
  description = "ALB URL (always HTTP; useful before HTTPS is ready)"
  value       = "http://${aws_lb.main.dns_name}"
}




