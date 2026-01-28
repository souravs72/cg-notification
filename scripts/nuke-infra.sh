#!/usr/bin/env bash
# ☢️ NUKES ALL INFRA CREATED BY TERRAFORM + OUT-OF-BAND RESOURCES
# NO SAFETY. NO PROMPTS. NO RECOVERY.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="${TERRAFORM_DIR:-$PROJECT_ROOT/terraform}"

export AWS_PROFILE="${AWS_PROFILE:-sourav-admin}"
export AWS_REGION="${AWS_REGION:-ap-south-1}"

CLUSTER_NAME="cg-notification-cluster"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

echo "=========================================="
echo "☢️  NUKING ALL cg-notification INFRA"
echo "Account: $ACCOUNT_ID"
echo "Region : $AWS_REGION"
echo "=========================================="
echo ""
echo "⚠️  THIS SCRIPT WILL:"
echo "⚠️   - MODIFY TERRAFORM FILES TEMPORARILY"
echo "⚠️   - DESTROY ALL INFRA IN THIS ACCOUNT/REGION"
echo ""
sleep 3
echo ""

# -------------------------------------------------------------------
# ECS
# -------------------------------------------------------------------
echo "💣 Scaling ECS services to zero..."

CLUSTER_STATUS="$(aws ecs describe-clusters \
  --clusters "$CLUSTER_NAME" \
  --region "$AWS_REGION" \
  --query 'clusters[0].status' \
  --output text 2>/dev/null || echo "MISSING")"

if [[ "$CLUSTER_STATUS" == "ACTIVE" ]]; then
  for svc in notification-api-service email-worker-service whatsapp-worker-service; do
    aws ecs update-service \
      --cluster "$CLUSTER_NAME" \
      --service "$svc" \
      --desired-count 0 \
      --region "$AWS_REGION" || true
  done
  sleep 10
else
  echo "  ⚠️ ECS cluster $CLUSTER_NAME not ACTIVE (status: $CLUSTER_STATUS), skipping"
fi

# -------------------------------------------------------------------
# ECS TASK DEFINITIONS
# -------------------------------------------------------------------
echo "💣 Deregistering ECS task definitions..."
aws ecs list-task-definitions \
  --family-prefix cg-notification \
  --region "$AWS_REGION" \
  --query 'taskDefinitionArns[]' \
  --output text | tr '\t' '\n' | while read -r td; do
    aws ecs deregister-task-definition \
      --task-definition "$td" \
      --region "$AWS_REGION" || true
done

# -------------------------------------------------------------------
# RDS
# -------------------------------------------------------------------
echo "💣 Disabling RDS deletion protection..."
aws rds modify-db-instance \
  --db-instance-identifier cg-notification-db \
  --no-deletion-protection \
  --apply-immediately \
  --region "$AWS_REGION" || true

# -------------------------------------------------------------------
# S3
# -------------------------------------------------------------------
echo "💣 Nuking S3 buckets..."

TF_BUCKET="$(terraform -chdir="$TERRAFORM_DIR" output -raw s3_bucket_name 2>/dev/null || true)"
if [[ -n "$TF_BUCKET" ]]; then
  if aws s3api head-bucket --bucket "$TF_BUCKET" --region "$AWS_REGION" 2>/dev/null; then
    echo "  ☠️ Deleting bucket $TF_BUCKET"
    aws s3 rb "s3://$TF_BUCKET" --force --region "$AWS_REGION" || true
  else
    echo "  ⚠️ Bucket $TF_BUCKET already gone"
  fi
fi

ALB_LOG_BUCKET="cg-notification-alb-logs-$ACCOUNT_ID"
if aws s3api head-bucket --bucket "$ALB_LOG_BUCKET" --region "$AWS_REGION" 2>/dev/null; then
  echo "  ☠️ Deleting ALB log bucket $ALB_LOG_BUCKET"
  aws s3 rb "s3://$ALB_LOG_BUCKET" --force --region "$AWS_REGION" || true
else
  echo "  ⚠️ Bucket $ALB_LOG_BUCKET already gone"
fi

# -------------------------------------------------------------------
# SECRETS MANAGER
# -------------------------------------------------------------------
echo "💣 Nuking Secrets Manager secrets..."
aws secretsmanager list-secrets \
  --query "SecretList[?contains(Name,'cg-notification')].Name" \
  --output text \
  --region "$AWS_REGION" | tr '\t' '\n' | while read -r secret; do
    aws secretsmanager delete-secret \
      --secret-id "$secret" \
      --force-delete-without-recovery \
      --region "$AWS_REGION" || true
done

# -------------------------------------------------------------------
# SSM PARAMETER STORE
# -------------------------------------------------------------------
echo "💣 Nuking SSM parameters..."
aws ssm describe-parameters \
  --query "Parameters[?contains(Name,'cg-notification')].Name" \
  --output text \
  --region "$AWS_REGION" | tr '\t' '\n' | while read -r param; do
    aws ssm delete-parameter \
      --name "$param" \
      --region "$AWS_REGION" || true
done

# -------------------------------------------------------------------
# ECR
# -------------------------------------------------------------------
echo "💣 Nuking ECR repositories..."
aws ecr describe-repositories \
  --query "repositories[?contains(repositoryName,'cg-notification')].repositoryName" \
  --output text \
  --region "$AWS_REGION" | tr '\t' '\n' | while read -r repo; do
    aws ecr delete-repository \
      --repository-name "$repo" \
      --force \
      --region "$AWS_REGION" || true
done

# -------------------------------------------------------------------
# MSK TOPICS
# -------------------------------------------------------------------
echo "💣 Deleting MSK topics..."
if [ -x "$SCRIPT_DIR/delete-msk-topics.sh" ]; then
  "$SCRIPT_DIR/delete-msk-topics.sh" || true
else
  echo "  ⚠️ delete-msk-topics.sh not found — skipping"
fi

# -------------------------------------------------------------------
# TERRAFORM (authoritative destroy)
# -------------------------------------------------------------------
echo "💣 Removing prevent_destroy lifecycle blocks..."
cd "$TERRAFORM_DIR"

LIFECYCLE_FILES=(s3.tf rds.tf msk.tf redis.tf cloudwatch.tf)

restore_backups() {
  echo ""
  echo "💣 Restoring lifecycle blocks..."
  for f in "${LIFECYCLE_FILES[@]}"; do
    [[ -f "$f.nuke-backup" ]] && mv "$f.nuke-backup" "$f" && echo "  ✓ Restored $f"
  done
}
trap restore_backups EXIT

for f in "${LIFECYCLE_FILES[@]}"; do
  if [[ -f "$f" ]]; then
    cp "$f" "$f.nuke-backup"
    sed -i '/^[[:space:]]*lifecycle {/,/^[[:space:]]*}$/s/^\([[:space:]]*\)/\1# /' "$f"
    echo "  ✓ Disabled lifecycle in $f"
  fi
done



echo ""
echo "💣 Terraform refresh/apply..."
terraform apply -auto-approve -refresh-only || terraform apply -auto-approve

echo ""
echo "💣 Terraform destroy..."
# Allow terraform destroy to fail so we can handle known edge cases (e.g. RDS final snapshot conflicts)
set +e
terraform destroy -auto-approve
DESTROY_EXIT_CODE=$?
set -e

if [ "$DESTROY_EXIT_CODE" -ne 0 ]; then
  echo ""
  echo "⚠️ terraform destroy failed, attempting forced cleanup for RDS..."
  aws rds delete-db-instance \
    --db-instance-identifier cg-notification-db \
    --skip-final-snapshot \
    --delete-automated-backups \
    --region "$AWS_REGION" || true

  echo ""
  echo "💣 Re-running Terraform destroy to clean up remaining resources..."
  set +e
  terraform destroy -auto-approve || true
  set -e
fi

trap - EXIT
restore_backups
cd "$PROJECT_ROOT"

# -------------------------------------------------------------------
# CLOUDWATCH
# -------------------------------------------------------------------
echo "💣 Nuking CloudWatch log groups..."
aws logs describe-log-groups \
  --query "logGroups[?contains(logGroupName,'cg-notification')].logGroupName" \
  --output text \
  --region "$AWS_REGION" | tr '\t' '\n' | while read -r lg; do
    aws logs delete-log-group --log-group-name "$lg" --region "$AWS_REGION" || true
done

echo "💣 Nuking CloudWatch alarms..."
aws cloudwatch describe-alarms \
  --query "MetricAlarms[?contains(AlarmName,'cg-notification')].AlarmName" \
  --output text \
  --region "$AWS_REGION" | tr '\t' '\n' | while read -r alarm; do
    aws cloudwatch delete-alarms --alarm-names "$alarm" --region "$AWS_REGION" || true
done

# -------------------------------------------------------------------
# KMS
# -------------------------------------------------------------------
echo "💣 Scheduling KMS key deletion..."
aws kms list-aliases \
  --query "Aliases[?contains(AliasName,'cg-notification')].TargetKeyId" \
  --output text \
  --region "$AWS_REGION" | tr '\t' '\n' | while read -r key; do
    aws kms schedule-key-deletion \
      --key-id "$key" \
      --pending-window-in-days 7 \
      --region "$AWS_REGION" || true
done

echo ""
echo "=========================================="
echo "☠️  N U K E   C O M P L E T E"
echo "=========================================="
