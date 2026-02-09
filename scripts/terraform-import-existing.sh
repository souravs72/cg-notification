#!/usr/bin/env bash
# Import existing AWS resources into Terraform state.
# Run from project root. Use when Terraform state was lost but resources exist in AWS.
#
# Prerequisites:
#   - AWS credentials configured
#   - terraform init already run
#   - Run from project root: ./scripts/terraform-import-existing.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="${TERRAFORM_DIR:-$PROJECT_ROOT/terraform}"
AWS_REGION="${AWS_REGION:-ap-south-1}"
ACCOUNT_ID="${ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text 2>/dev/null)}"

cd "$TERRAFORM_DIR"

echo "Importing existing resources (region=$AWS_REGION, account=$ACCOUNT_ID)..."
echo ""

# S3 buckets (names from Terraform config)
terraform import -input=false 'aws_s3_bucket.alb_logs' "cg-notification-alb-logs-${ACCOUNT_ID}" 2>/dev/null || true
# Uploads: try default name; if using var.s3_bucket_name, discover from AWS
UPLOADS_BUCKET=$(aws s3api list-buckets --query "Buckets[?contains(Name,'cg-notification-uploads')].Name" --output text 2>/dev/null | head -1)
if [ -n "$UPLOADS_BUCKET" ]; then
  terraform import -input=false 'aws_s3_bucket.uploads' "$UPLOADS_BUCKET" 2>/dev/null || true
else
  terraform import -input=false 'aws_s3_bucket.uploads' "cg-notification-uploads-${ACCOUNT_ID}" 2>/dev/null || true
fi

# ECR repositories (repository name uses / in path)
terraform import -input=false 'aws_ecr_repository.api' 'cg-notification/api' 2>/dev/null || true
terraform import -input=false 'aws_ecr_repository.email_worker' 'cg-notification/email-worker' 2>/dev/null || true
terraform import -input=false 'aws_ecr_repository.whatsapp_worker' 'cg-notification/whatsapp-worker' 2>/dev/null || true
terraform import -input=false 'aws_ecr_repository.migration' 'cg-notification/migration' 2>/dev/null || true

# CloudWatch log groups
terraform import -input=false 'aws_cloudwatch_log_group.api' '/ecs/cg-notification-api' 2>/dev/null || true
terraform import -input=false 'aws_cloudwatch_log_group.email_worker' '/ecs/cg-notification-email-worker' 2>/dev/null || true
terraform import -input=false 'aws_cloudwatch_log_group.whatsapp_worker' '/ecs/cg-notification-whatsapp-worker' 2>/dev/null || true

# IAM roles
terraform import -input=false 'aws_iam_role.ecs_task_execution' 'cg-notification-ecs-task-execution-role' 2>/dev/null || true
terraform import -input=false 'aws_iam_role.api_task' 'cg-api-task-role' 2>/dev/null || true
terraform import -input=false 'aws_iam_role.email_worker_task' 'cg-email-worker-task-role' 2>/dev/null || true
terraform import -input=false 'aws_iam_role.whatsapp_worker_task' 'cg-whatsapp-worker-task-role' 2>/dev/null || true
terraform import -input=false 'aws_iam_role.rds_proxy' 'cg-notification-rds-proxy-role' 2>/dev/null || true

# RDS subnet group, RDS instance, ElastiCache subnet group, ElastiCache replication group
terraform import -input=false 'aws_db_subnet_group.main' 'cg-notification-db-subnet' 2>/dev/null || true
terraform import -input=false 'aws_db_instance.main' 'cg-notification-db' 2>/dev/null || true
terraform import -input=false 'aws_elasticache_subnet_group.redis' 'cg-notification-redis-subnets' 2>/dev/null || true
terraform import -input=false 'aws_elasticache_replication_group.redis' 'cg-notification-redis' 2>/dev/null || true

# KMS alias (use alias name without arn prefix)
terraform import -input=false 'aws_kms_alias.rds' 'alias/cg-notification-rds' 2>/dev/null || true

# Secrets Manager (secret name vs terraform resource)
terraform import -input=false 'aws_secretsmanager_secret.db_password' 'cg-notification/db-password' 2>/dev/null || true
terraform import -input=false 'aws_secretsmanager_secret.db_password_only' 'cg-notification/db-password-only' 2>/dev/null || true
terraform import -input=false 'aws_secretsmanager_secret.redis_password' 'cg-notification/redis-password' 2>/dev/null || true
terraform import -input=false 'aws_secretsmanager_secret.sendgrid_api_key' 'cg-notification/sendgrid-api-key' 2>/dev/null || true
terraform import -input=false 'aws_secretsmanager_secret.sendgrid_from_email' 'cg-notification/sendgrid-from-email' 2>/dev/null || true
terraform import -input=false 'aws_secretsmanager_secret.sendgrid_from_name' 'cg-notification/sendgrid-from-name' 2>/dev/null || true
terraform import -input=false 'aws_secretsmanager_secret.wasender_api_key' 'cg-notification/wasender-api-key' 2>/dev/null || true
terraform import -input=false 'aws_secretsmanager_secret.admin_api_key' 'cg-notification/admin-api-key' 2>/dev/null || true

# WAF - import format is ID/NAME/SCOPE (not ARN)
WAF_ID=$(aws wafv2 list-web-acls --scope REGIONAL --region "$AWS_REGION" --query "WebACLs[?Name=='cg-notification-waf'].Id" --output text 2>/dev/null | awk '{print $1}')
if [ -n "$WAF_ID" ] && [ "$WAF_ID" != "None" ]; then
  terraform import -input=false 'aws_wafv2_web_acl.main' "${WAF_ID}/cg-notification-waf/REGIONAL" 2>/dev/null || true
fi

# Target group - need ARN
TG_ARN=$(aws elbv2 describe-target-groups --names cg-notification-api-tg --region "$AWS_REGION" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null)
if [ -n "$TG_ARN" ] && [ "$TG_ARN" != "None" ]; then
  terraform import -input=false 'aws_lb_target_group.api' "$TG_ARN" 2>/dev/null || true
fi

# ALB - need ARN
ALB_ARN=$(aws elbv2 describe-load-balancers --names cg-notification-alb --region "$AWS_REGION" --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null)
if [ -n "$ALB_ARN" ] && [ "$ALB_ARN" != "None" ]; then
  terraform import -input=false 'aws_lb.main' "$ALB_ARN" 2>/dev/null || true
fi

# RDS Proxy (by name)
terraform import -input=false 'aws_db_proxy.main' 'cg-notification-db-proxy' 2>/dev/null || true

# KMS alias S3 (alias may exist pointing to pre-created key)
terraform import -input=false 'aws_kms_alias.s3' 'alias/cg-notification-s3' 2>/dev/null || true

# ECS services (format: cluster-name/service-name)
terraform import -input=false 'aws_ecs_service.api' 'cg-notification-cluster/notification-api-service' 2>/dev/null || true
terraform import -input=false 'aws_ecs_service.email_worker' 'cg-notification-cluster/email-worker-service' 2>/dev/null || true
terraform import -input=false 'aws_ecs_service.whatsapp_worker' 'cg-notification-cluster/whatsapp-worker-service' 2>/dev/null || true

echo ""
echo "Import complete. Run 'terraform plan' to see remaining changes."
