#!/usr/bin/env bash
# App-only deployment: Build JARs → Docker images → Push to ECR → Force ECS rollout.
# Use this when you changed app code only (no infra changes).
#
# Prerequisites: Infrastructure already deployed (run deploy-trigger.sh once first).
#
# Usage:
#   ./scripts/deploy-to-aws.sh                    # Deploy with tag 'latest'
#   ./scripts/deploy-to-aws.sh --image-tag=v1.0.0 # Deploy with specific tag
#   IMAGE_TAG=$(git rev-parse --short HEAD) ./scripts/deploy-to-aws.sh

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="${TERRAFORM_DIR:-$PROJECT_ROOT/terraform}"

export AWS_PROFILE="${AWS_PROFILE:-sourav-admin}"
export AWS_REGION="${AWS_REGION:-ap-south-1}"
export IMAGE_TAG="${IMAGE_TAG:-latest}"

CLUSTER_NAME="cg-notification-cluster"
SERVICES=("notification-api-service" "email-worker-service" "whatsapp-worker-service")

# Parse args
for arg in "$@"; do
  case "$arg" in
    --image-tag=*) IMAGE_TAG="${arg#*=}" ;;
    --help|-h)
      cat <<EOF
Usage: $0 [OPTIONS]

App-only deployment (no Terraform, migrations, or secrets).
Builds JARs, Docker images, pushes to ECR, forces ECS rollout.

Options:
  --image-tag=TAG   Docker image tag (default: latest)

Environment:
  AWS_PROFILE       AWS profile (default: sourav-admin)
  AWS_REGION        AWS region (default: ap-south-1)
  IMAGE_TAG         Image tag (default: latest)

Examples:
  $0
  $0 --image-tag=\$(git rev-parse --short HEAD)
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (use --help)"
      exit 1
      ;;
  esac
done

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${CYAN}ℹ${NC} $1"; }
log_ok() { echo -e "${GREEN}✓${NC} $1"; }
log_err() { echo -e "${RED}✗${NC} $1" >&2; }

echo ""
echo "=========================================="
echo "  App-Only Deployment to AWS"
echo "=========================================="
echo ""
log_info "Image tag: $IMAGE_TAG"
log_info "Region: $AWS_REGION"
echo ""

# Prereqs
for cmd in aws docker mvn; do
  if ! command -v "$cmd" &>/dev/null; then
    log_err "$cmd is required"
    exit 1
  fi
done

if ! aws sts get-caller-identity &>/dev/null; then
  log_err "AWS credentials invalid. Set AWS_PROFILE or configure credentials."
  exit 1
fi

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
log_ok "AWS account: $ACCOUNT_ID"
echo ""

# Terraform outputs (requires prior full deploy so state exists)
cd "$TERRAFORM_DIR"
terraform init -input=false 2>/dev/null || true
ECR_API_URL="$(terraform output -raw ecr_api_repository_url 2>/dev/null || true)"
ECR_EMAIL_URL="$(terraform output -raw ecr_email_worker_repository_url 2>/dev/null || true)"
ECR_WHATSAPP_URL="$(terraform output -raw ecr_whatsapp_worker_repository_url 2>/dev/null || true)"
cd "$PROJECT_ROOT"

if [ -z "$ECR_API_URL" ] || [ -z "$ECR_EMAIL_URL" ] || [ -z "$ECR_WHATSAPP_URL" ]; then
  log_err "Could not get ECR URLs from Terraform. Run full deployment first: ./scripts/deploy-trigger.sh"
  exit 1
fi

log_ok "ECR repos found"
echo ""

# 1. Maven build (Dockerfiles expect pre-built JARs)
echo "=========================================="
echo "  Building JARs"
echo "=========================================="
log_info "Running Maven package..."
mvn -q -pl notification-api,email-worker,whatsapp-worker -am package -DskipTests
log_ok "JARs built"
echo ""

# 2. ECR login
log_info "Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin \
  "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com" >/dev/null 2>&1 || true
log_ok "ECR login done"
echo ""

# 3. Build and push
echo "=========================================="
echo "  Building & Pushing Docker Images"
echo "=========================================="

build_push() {
  local name=$1
  local dockerfile=$2
  local ecr_url=$3
  log_info "Building $name..."
  docker build -f "$dockerfile" -t "${ecr_url}:${IMAGE_TAG}" . || { log_err "Build failed: $name"; exit 1; }
  docker tag "${ecr_url}:${IMAGE_TAG}" "${ecr_url}:latest"
  log_info "Pushing $name..."
  docker push "${ecr_url}:${IMAGE_TAG}"
  docker push "${ecr_url}:latest"
  log_ok "$name pushed"
}

build_push "notification-api" "notification-api/Dockerfile" "$ECR_API_URL"
echo ""
build_push "email-worker" "email-worker/Dockerfile" "$ECR_EMAIL_URL"
echo ""
build_push "whatsapp-worker" "whatsapp-worker/Dockerfile" "$ECR_WHATSAPP_URL"
echo ""

# 4. Update task defs if custom tag (ECS uses image:tag in task definition)
if [ "$IMAGE_TAG" != "latest" ]; then
  echo "=========================================="
  echo "  Updating ECS Task Definitions"
  echo "=========================================="
  cd "$TERRAFORM_DIR"
  terraform apply -var="image_tag=$IMAGE_TAG" \
    -target=aws_ecs_task_definition.api \
    -target=aws_ecs_task_definition.email_worker \
    -target=aws_ecs_task_definition.whatsapp_worker \
    -auto-approve -input=false
  cd "$PROJECT_ROOT"
  echo ""
fi

# 5. Force ECS rollout
echo "=========================================="
echo "  Triggering ECS Rollout"
echo "=========================================="
for svc in "${SERVICES[@]}"; do
  log_info "Updating $svc..."
  aws ecs update-service \
    --cluster "$CLUSTER_NAME" \
    --service "$svc" \
    --force-new-deployment \
    --region "$AWS_REGION" \
    --query 'service.[serviceName,status,desiredCount,runningCount]' \
    --output table
  log_ok "$svc rollout triggered"
done

echo ""
echo "=========================================="
echo "  Deployment Initiated"
echo "=========================================="
echo ""
log_ok "Images pushed and ECS rollout triggered. Services typically stabilize in 5–10 minutes."
echo ""
echo "Monitor:"
echo "  aws ecs describe-services --cluster $CLUSTER_NAME --services ${SERVICES[*]} --region $AWS_REGION --query 'services[*].[serviceName,runningCount,desiredCount]' --output table"
echo ""
echo "Logs:"
echo "  aws logs tail /ecs/cg-notification-api --follow --region $AWS_REGION"
echo ""
ALB_DNS=$(terraform -chdir="$TERRAFORM_DIR" output -raw alb_dns_name 2>/dev/null || true)
if [ -n "$ALB_DNS" ]; then
  echo "Health: curl http://${ALB_DNS}/actuator/health"
  echo ""
fi
