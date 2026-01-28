variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Environment name (e.g., prod, staging)"
  type        = string
  default     = "prod"
}

variable "image_tag" {
  description = "Docker image tag for ECS tasks (e.g. Git SHA). Use 'latest' for convenience; set to SHA for rollback-safe deploys."
  type        = string
  default     = "latest"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = ["ap-south-1a", "ap-south-1b"]
}

variable "notification_api_desired_count" {
  description = "Desired number of notification-api tasks"
  type        = number
  default     = 2
}

variable "email_worker_desired_count" {
  description = "Desired number of email-worker tasks"
  type        = number
  default     = 2
}

variable "whatsapp_worker_desired_count" {
  description = "Desired number of whatsapp-worker tasks"
  type        = number
  default     = 2
}

variable "rds_instance_class" {
  description = "RDS instance class (db.t3.micro is too small for production - use db.t3.small minimum)"
  type        = string
  default     = "db.t3.small"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "rds_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "15.15" # Updated: 15.4 not available, using latest 15.x
}

variable "rds_db_name" {
  description = "RDS database name"
  type        = string
  default     = "notification_db"
}

variable "rds_master_username" {
  description = "RDS master username"
  type        = string
  default     = "notification_user"
}

variable "s3_bucket_name" {
  description = "S3 bucket name for uploads (must be globally unique)"
  type        = string
  default     = ""
}

variable "msk_cluster_name" {
  description = "MSK Serverless cluster name"
  type        = string
  default     = "cg-notification-msk"
}

variable "ecs_cluster_name" {
  description = "ECS cluster name"
  type        = string
  default     = "cg-notification-cluster"
}

variable "alb_name" {
  description = "Application Load Balancer name"
  type        = string
  default     = "cg-notification-alb"
}

variable "file_upload_base_url" {
  description = "Base URL for file uploads (used in API service)"
  type        = string
  default     = ""
}

variable "alb_deletion_protection" {
  description = "Enable deletion protection for the Application Load Balancer"
  type        = bool
  default     = false
}

variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log retention in days for ECS service logs (prevents slow, silent cost creep)"
  type        = number
  default     = 14
}

variable "acm_certificate_arn" {
  description = "ACM certificate ARN for HTTPS listener (optional, must be in same region as ALB). If domain_name is provided, this will be auto-created."
  type        = string
  default     = ""
}

variable "domain_name" {
  description = "Custom domain name for the application (e.g., notifications.example.com). If provided, ACM certificate will be created."
  type        = string
  default     = ""
}

variable "route53_zone_id" {
  description = "Route53 hosted zone ID (e.g., Z123456ABCDEFG). If provided along with domain_name, DNS records will be created automatically. If omitted, DNS validation records will need to be set manually."
  type        = string
  default     = ""
}

variable "enable_db_migrations" {
  description = "Enable database migrations via null_resource (NOT recommended for production/CI). Defaults to false for safety. ⚠️ WARNING: null_resource migrations run from operator's laptop, require psql locally, break in CI, and are non-idempotent. For production, use ECS task-based migrations instead (see DEPLOYMENT.md)."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type for Spring Session"
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_engine_version" {
  description = "ElastiCache Redis engine version"
  type        = string
  default     = "7.0"
}

variable "configure_docker_ecr_credentials" {
  description = "When true, run setup-docker-ecr-credential-helper.sh after apply to configure Docker ECR credential helper (removes 'credentials stored unencrypted' warning). Runs locally on the machine executing Terraform. Requires: jq, curl, AWS CLI configured."
  type        = bool
  default     = false
}

