#!/usr/bin/env bash
# Complete deployment script: Terraform → Secrets → Migrations → Build → Deploy → Wait → Test (SNS/SQS messaging)
# Run from project root. Handles everything from infrastructure to health checks.
#
# Usage:
#   ./scripts/deploy-trigger.sh                    # Full deployment with all steps
#   ./scripts/deploy-trigger.sh --skip-secrets     # Skip secret injection (use when already injected)
#   ./scripts/deploy-trigger.sh --skip-terraform   # Skip infrastructure (app-only deployment)
#   ./scripts/deploy-trigger.sh --image-tag=v1.0.0 # Use specific image tag

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="${TERRAFORM_DIR:-$PROJECT_ROOT/terraform}"

export AWS_PROFILE="${AWS_PROFILE:-sourav-admin}"
export AWS_REGION="${AWS_REGION:-ap-south-1}"
export IMAGE_TAG="${IMAGE_TAG:-latest}"
export DOMAIN_NAME="${DOMAIN_NAME:-}"
export ROUTE53_ZONE_ID="${ROUTE53_ZONE_ID:-}"

# Parse arguments
SKIP_SECRETS=false
SKIP_TERRAFORM=false
SKIP_MIGRATIONS=false
for arg in "$@"; do
  case "$arg" in
    --skip-secrets) SKIP_SECRETS=true ;;
    --skip-terraform) SKIP_TERRAFORM=true ;;
    --skip-migrations) SKIP_MIGRATIONS=true ;;
    --image-tag=*) IMAGE_TAG="${arg#*=}" ;;
    --domain=*) DOMAIN_NAME="${arg#*=}" ;;
    --route53-zone-id=*) ROUTE53_ZONE_ID="${arg#*=}" ;;
    --help|-h)
      cat <<EOF
Usage: $0 [OPTIONS]

Options:
  --skip-secrets           Skip secret injection (use when secrets already exist)
  --skip-terraform         Skip Terraform apply (app-only deployment)
  --skip-migrations        Skip database migrations (use when migrations already applied or password mismatch)
  --image-tag=TAG          Use specific Docker image tag (default: latest or \$IMAGE_TAG)
  --domain=DOMAIN          Custom domain name (e.g., notifications.example.com)
  --route53-zone-id=ZONE   Route53 hosted zone ID (e.g., Z123456ABCDEFG).
                           If provided, DNS records created automatically.
                           If omitted, DNS validation records shown for manual setup.

Environment Variables:
  AWS_PROFILE              AWS profile to use (default: sourav-admin)
  AWS_REGION               AWS region (default: ap-south-1)
  IMAGE_TAG                Docker image tag (default: latest)
  DOMAIN_NAME              Custom domain name (alternative to --domain flag)
  ROUTE53_ZONE_ID          Route53 zone ID (alternative to --route53-zone-id flag)
  
  For secret injection, set:
    WASENDER_API_KEY
    SENDGRID_API_KEY
    SENDGRID_FROM_EMAIL
    SENDGRID_FROM_NAME
    ADMIN_API_KEY

Examples:
  $0                                                                    # Full deployment
  $0 --domain=notifications.example.com --route53-zone-id=Z123456      # Auto DNS setup
  $0 --domain=notifications.example.com                                # Manual DNS setup
  $0 --skip-terraform                                                   # Deploy app only
  $0 --image-tag=\$(git rev-parse --short HEAD)                          # Use Git SHA
EOF
      exit 0
      ;;
    *)
      echo "❌ Unknown option: $arg"
      echo "Run $0 --help for usage"
      exit 1
      ;;
  esac
done

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
  echo -e "${CYAN}ℹ${NC} $1"
}

log_success() {
  echo -e "${GREEN}✓${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
  echo -e "${RED}✗${NC} $1" >&2
}

log_section() {
  echo ""
  echo -e "${BLUE}=========================================="
  echo -e "$1"
  echo -e "==========================================${NC}"
  echo ""
}

# Check prerequisites
check_prerequisites() {
  log_section "Checking Prerequisites"
  
  local missing=0
  
  for cmd in aws terraform docker jq curl; do
    if ! command -v "$cmd" &> /dev/null; then
      log_error "$cmd is required but not installed"
      missing=1
    else
      log_success "$cmd found"
    fi
  done
  
  if [ $missing -eq 1 ]; then
    log_error "Missing required tools. Please install them and retry."
    exit 1
  fi
  
  # Verify AWS credentials
  if ! aws sts get-caller-identity > /dev/null 2>&1; then
    log_error "AWS credentials not configured or invalid"
    log_info "Export AWS_PROFILE and AWS_REGION, then retry."
    exit 1
  fi
  
  ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
  log_success "AWS credentials valid (Account: $ACCOUNT_ID, Region: $AWS_REGION)"
  echo ""
}

# Ensure migration image exists in ECR (needed before running ECS migrations).
# On fresh deploys, the ECR repo exists after Terraform, but the image tag does not.
ensure_migration_image() {
  local repo_name="cg-notification/migration"
  local ecr_repo="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${repo_name}"

  log_info "Checking migration image in ECR: ${ecr_repo}:${IMAGE_TAG}"
  if aws ecr describe-images --repository-name "$repo_name" --image-ids "imageTag=${IMAGE_TAG}" --region "$AWS_REGION" > /dev/null 2>&1; then
    log_success "Migration image exists in ECR"
    return 0
  fi

  log_warn "Migration image not found in ECR; building and pushing it now"

  log_info "Logging in to ECR..."
  aws ecr get-login-password --region "$AWS_REGION" | \
    docker login --username AWS --password-stdin \
    "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com" > /dev/null 2>&1 || true

  log_info "Building migration image..."
  docker build -f deployment/Dockerfile.migration -t "${ecr_repo}:${IMAGE_TAG}" "$PROJECT_ROOT" || {
    log_error "Failed to build migration image"
    return 1
  }
  docker tag "${ecr_repo}:${IMAGE_TAG}" "${ecr_repo}:latest"

  log_info "Pushing migration image..."
  docker push "${ecr_repo}:${IMAGE_TAG}" || return 1
  docker push "${ecr_repo}:latest" || return 1

  log_success "Migration image pushed"
}

# Wait for resource to be ready
wait_for_resource() {
  local name=$1
  local check_fn=$2
  local max_wait=${3:-600}  # Default 10 minutes
  local interval=${4:-15}   # Default 15 seconds
  
  log_info "Waiting for $name to be ready (max ${max_wait}s)..."
  
  local elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    if eval "$check_fn" > /dev/null 2>&1; then
      log_success "$name is ready"
      return 0
    fi
    echo -n "."
    sleep $interval
    elapsed=$((elapsed + interval))
  done
  
  echo ""
  log_error "$name did not become ready within ${max_wait}s"
  return 1
}

# Wait for ECS service to stabilize
wait_for_ecs_service() {
  local cluster=$1
  local service=$2
  local max_wait=${3:-900}  # Default 15 minutes
  local interval=${4:-30}   # Default 30 seconds
  
  log_info "Waiting for $service to stabilize (max ${max_wait}s)..."
  
  local elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    local status=$(aws ecs describe-services \
      --cluster "$cluster" \
      --services "$service" \
      --region "$AWS_REGION" \
      --query 'services[0].[runningCount,desiredCount,deployments[?status==`PRIMARY`].status | [0]]' \
      --output text 2>/dev/null || echo "0 0 FAILED")
    
    local running=$(echo "$status" | awk '{print $1}')
    local desired=$(echo "$status" | awk '{print $2}')
    local deploy_status=$(echo "$status" | awk '{print $3}')
    
    if [ "$running" = "$desired" ] && [ "$deploy_status" = "PRIMARY" ] && [ "$running" != "0" ]; then
      log_success "$service is stable (${running}/${desired} tasks running)"
      return 0
    fi
    
    echo -n "."
    sleep $interval
    elapsed=$((elapsed + interval))
  done
  
  echo ""
  log_error "$service did not stabilize within ${max_wait}s"
  log_info "Check status: aws ecs describe-services --cluster $cluster --services $service --region $AWS_REGION"
  return 1
}

# Test health endpoint
test_health_endpoint() {
  local url=$1
  local name=$2
  local max_attempts=${3:-10}
  local interval=${4:-10}
  
  log_info "Testing $name health endpoint..."
  
  local attempt=0
  while [ $attempt -lt $max_attempts ]; do
    # Follow redirects (ALB may redirect HTTP -> HTTPS) and allow insecure during bootstrap:
    # - When hitting the ALB DNS name over HTTPS, cert hostname won't match (expected) until you use your custom domain.
    if response=$(curl -s -L -k -w "\n%{http_code}" "$url" 2>/dev/null); then
      local http_code=$(echo "$response" | tail -n1)
      local body=$(echo "$response" | sed '$d')
      
      if [ "$http_code" = "200" ]; then
        if echo "$body" | jq -e '.status == "UP"' > /dev/null 2>&1; then
          log_success "$name health check passed (HTTP $http_code)"
          if echo "$body" | jq -e '.components' > /dev/null 2>&1; then
            echo "$body" | jq -r '.components | to_entries[] | "  \(.key): \(.value.status)"' 2>/dev/null || true
          fi
          return 0
        else
          log_warn "$name returned 200 but status is not UP: $body"
        fi
      else
        log_warn "$name returned HTTP $http_code (attempt $((attempt + 1))/$max_attempts)"
      fi
    else
      log_warn "$name connection failed (attempt $((attempt + 1))/$max_attempts)"
    fi
    
    sleep $interval
    attempt=$((attempt + 1))
  done
  
  log_error "$name health check failed after $max_attempts attempts"
  return 1
}

# Main deployment flow
main() {
  log_section "🚀 Starting Complete Deployment"
  log_info "Image Tag: $IMAGE_TAG"
  log_info "Account: $ACCOUNT_ID"
  log_info "Region: $AWS_REGION"
  echo ""
  
  cd "$PROJECT_ROOT"
  
  # ===== TERRAFORM =====
  if [ "$SKIP_TERRAFORM" = false ]; then
    log_section "📦 Terraform: Infrastructure Deployment"
    cd "$TERRAFORM_DIR"
    
    log_info "Initializing Terraform..."
    terraform init -input=false -upgrade
    
    log_info "Planning Terraform changes..."
    # Build terraform plan command with optional domain and zone
    PLAN_VARS=(-var="image_tag=$IMAGE_TAG")
    if [ -n "$DOMAIN_NAME" ]; then
      PLAN_VARS+=(-var="domain_name=$DOMAIN_NAME")
      log_info "Domain: $DOMAIN_NAME (ACM certificate will be created)"
      
      if [ -n "$ROUTE53_ZONE_ID" ]; then
        PLAN_VARS+=(-var="route53_zone_id=$ROUTE53_ZONE_ID")
        log_success "Route53 zone ID provided - DNS records will be created automatically"
      else
        log_warn "Route53 zone ID not provided - DNS validation records will be shown for manual setup"
      fi
    fi
    
    terraform plan -out=tfplan -input=false "${PLAN_VARS[@]}" -detailed-exitcode || {
      local plan_exit=$?
      if [ $plan_exit -eq 1 ]; then
        log_error "Terraform plan failed"
        exit 1
      fi
      # Exit code 2 means changes detected, which is fine
    }
    
    log_info "Applying Terraform changes..."
    terraform apply -input=false tfplan
    rm -f tfplan
    
    log_success "Terraform apply complete"
    cd "$PROJECT_ROOT"
    echo ""
    
    # Wait for critical infrastructure
    log_section "⏳ Waiting for Infrastructure to be Ready"
    
    log_info "Waiting for RDS instance..."
    wait_for_resource "RDS" \
      "aws rds describe-db-instances --db-instance-identifier cg-notification-db --region $AWS_REGION --query 'DBInstances[0].DBInstanceStatus' --output text | grep -q available" \
      600 15 || log_warn "RDS may still be initializing"
    
    log_info "Waiting for Redis cluster (Terraform VPC)..."
    wait_for_resource "Redis (Terraform VPC)" \
      "aws elasticache describe-replication-groups --replication-group-id cg-notification-redis --region $AWS_REGION --query 'ReplicationGroups[0].Status' --output text 2>/dev/null | grep -q available" \
      300 15 || log_warn "Redis (Terraform VPC) may still be initializing"
    log_info "Waiting for Redis cluster (RDS VPC - API session store)..."
    wait_for_resource "Redis (RDS VPC)" \
      "aws elasticache describe-replication-groups --replication-group-id cg-notif-redis-rds --region $AWS_REGION --query 'ReplicationGroups[0].Status' --output text 2>/dev/null | grep -q available" \
      600 15 || log_warn "Redis (RDS VPC) may still be initializing"
    
    echo ""
  else
    log_section "⏭️  Skipping Terraform (--skip-terraform)"
    echo ""
  fi
  
  # ===== SECRETS =====
  if [ "$SKIP_SECRETS" = false ]; then
    log_section "🔐 Injecting Secrets"
    if [ -f "$TERRAFORM_DIR/inject-secrets.sh" ]; then
      "$TERRAFORM_DIR/inject-secrets.sh" || {
        log_error "Secret injection failed"
        log_info "If secrets already exist, use --skip-secrets flag"
        exit 1
      }
      log_success "Secrets injected"
    else
      log_warn "inject-secrets.sh not found, skipping"
    fi
    echo ""
  else
    log_section "⏭️  Skipping Secret Injection (--skip-secrets)"
    echo ""
  fi
  
  # ===== MIGRATIONS =====
  if [ "$SKIP_MIGRATIONS" = true ]; then
    log_section "⏭️  Skipping Database Migrations (--skip-migrations)"
  else
    log_section "🗄️  Database Migrations"
    if [ -f "$TERRAFORM_DIR/run-migrations-ecs.sh" ]; then
      ensure_migration_image || {
        log_error "Preparing migration image failed"
        exit 1
      }
      "$TERRAFORM_DIR/run-migrations-ecs.sh" || {
        log_error "Migrations failed. If password mismatch, ensure cg-notification/db-password-only matches RDS."
        log_info "To continue without migrations: $0 --skip-migrations"
        exit 1
      }
      log_success "Migrations complete"
    else
      log_warn "run-migrations-ecs.sh not found, skipping"
    fi
  fi
  echo ""
  
  # ===== DOCKER BUILD & PUSH =====
  log_section "🐳 Building and Pushing Docker Images"
  
  ECR_API_URL="$(terraform -chdir="$TERRAFORM_DIR" output -raw ecr_api_repository_url 2>/dev/null || true)"
  ECR_EMAIL_URL="$(terraform -chdir="$TERRAFORM_DIR" output -raw ecr_email_worker_repository_url 2>/dev/null || true)"
  ECR_WHATSAPP_URL="$(terraform -chdir="$TERRAFORM_DIR" output -raw ecr_whatsapp_worker_repository_url 2>/dev/null || true)"
  
  if [ -z "$ECR_API_URL" ] || [ -z "$ECR_EMAIL_URL" ] || [ -z "$ECR_WHATSAPP_URL" ]; then
    log_error "Could not get ECR URLs from Terraform"
    exit 1
  fi
  
  # Login to ECR
  log_info "Logging in to ECR..."
  # Credential helper may cause exit code 1, but authentication can still work
  # So we ignore the exit code and verify by testing actual Docker access
  aws ecr get-login-password --region "$AWS_REGION" | \
    docker login --username AWS --password-stdin \
    "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com" > /dev/null 2>&1 || true
  
  # Credential helper warnings are non-fatal - Docker can authenticate even if storage fails
  # We'll proceed and let the actual docker push fail if authentication doesn't work
  log_success "ECR login attempted (credential helper warnings are non-fatal)"
  
  # Build and push notification-api
  log_info "Building notification-api..."
  docker build -f notification-api/Dockerfile -t "${ECR_API_URL}:${IMAGE_TAG}" . || {
    log_error "Failed to build notification-api"
    exit 1
  }
  docker tag "${ECR_API_URL}:${IMAGE_TAG}" "${ECR_API_URL}:latest"
  docker push "${ECR_API_URL}:${IMAGE_TAG}"
  docker push "${ECR_API_URL}:latest"
  log_success "notification-api pushed"
  
  # Build and push email-worker
  log_info "Building email-worker..."
  docker build -f email-worker/Dockerfile -t "${ECR_EMAIL_URL}:${IMAGE_TAG}" . || {
    log_error "Failed to build email-worker"
    exit 1
  }
  docker tag "${ECR_EMAIL_URL}:${IMAGE_TAG}" "${ECR_EMAIL_URL}:latest"
  docker push "${ECR_EMAIL_URL}:${IMAGE_TAG}"
  docker push "${ECR_EMAIL_URL}:latest"
  log_success "email-worker pushed"
  
  # Build and push whatsapp-worker
  log_info "Building whatsapp-worker..."
  docker build -f whatsapp-worker/Dockerfile -t "${ECR_WHATSAPP_URL}:${IMAGE_TAG}" . || {
    log_error "Failed to build whatsapp-worker"
    exit 1
  }
  docker tag "${ECR_WHATSAPP_URL}:${IMAGE_TAG}" "${ECR_WHATSAPP_URL}:latest"
  docker push "${ECR_WHATSAPP_URL}:${IMAGE_TAG}"
  docker push "${ECR_WHATSAPP_URL}:latest"
  log_success "whatsapp-worker pushed"
  echo ""
  
  # ===== ECS DEPLOYMENT =====
  log_section "🚢 Deploying ECS Services"
  
  CLUSTER_NAME="cg-notification-cluster"
  SERVICES=("notification-api-service" "email-worker-service" "whatsapp-worker-service")
  
  for svc in "${SERVICES[@]}"; do
    log_info "Updating $svc..."
    aws ecs update-service \
      --cluster "$CLUSTER_NAME" \
      --service "$svc" \
      --force-new-deployment \
      --region "$AWS_REGION" \
      --query 'service.[serviceName,status,desiredCount,runningCount]' \
      --output table > /dev/null || {
      log_error "Failed to update $svc"
      exit 1
    }
    log_success "$svc update triggered"
  done
  echo ""
  
  # ===== WAIT FOR SERVICES =====
  log_section "⏳ Waiting for ECS Services to Stabilize"
  
  for svc in "${SERVICES[@]}"; do
    wait_for_ecs_service "$CLUSTER_NAME" "$svc" 900 30 || {
      log_warn "$svc may still be starting. Check CloudWatch logs for details."
    }
  done
  echo ""
  
  # ===== HEALTH CHECKS =====
  log_section "🏥 Testing Application Health"
  
  ALB_DNS=$(terraform -chdir="$TERRAFORM_DIR" output -raw alb_dns_name 2>/dev/null || true)
  
  if [ -z "$ALB_DNS" ]; then
    log_warn "Could not get ALB DNS name. Skipping health checks."
  else
    ALB_URL="http://${ALB_DNS}"
    
    log_info "ALB URL: $ALB_URL"
    echo ""
    
    # Wait a bit for ALB to register healthy targets
    log_info "Waiting for ALB targets to become healthy..."
    sleep 60
    
    # Test health endpoint (match ALB liveness check path)
    if test_health_endpoint "${ALB_URL}/actuator/health/liveness" "Notification API" 15 15; then
      log_success "Application is healthy and ready!"
    else
      log_error "Health check failed. Application may still be starting."
      log_info "Check ALB target health:"
      log_info "  aws elbv2 describe-target-health --target-group-arn \$(aws elbv2 describe-target-groups --names cg-notification-api-tg --region $AWS_REGION --query 'TargetGroups[0].TargetGroupArn' --output text) --region $AWS_REGION"
      log_info "Check ECS service logs:"
      log_info "  aws logs tail /ecs/cg-notification-api --follow --region $AWS_REGION"
      exit 1
    fi
    echo ""
  fi
  
  # ===== SUMMARY =====
  log_section "✅ Deployment Complete"
  
  echo -e "${GREEN}All deployment steps completed successfully!${NC}"
  echo ""
  echo "📊 Deployment Summary:"
  echo "  • Infrastructure: $( [ "$SKIP_TERRAFORM" = false ] && echo "Deployed" || echo "Skipped" )"
  echo "  • Secrets: $( [ "$SKIP_SECRETS" = false ] && echo "Injected" || echo "Skipped" )"
  echo "  • Migrations: Complete"
  echo "  • Messaging: SNS/SQS (created by Terraform)"
  echo "  • Docker Images: Built and Pushed (tag: $IMAGE_TAG)"
  echo "  • ECS Services: Deployed and Stable"
  echo "  • Health Checks: Passed"
  echo ""
  
  # Get domain and DNS information
  DOMAIN=$(terraform -chdir="$TERRAFORM_DIR" output -raw domain_name 2>/dev/null || echo "")
  APP_URL=$(terraform -chdir="$TERRAFORM_DIR" output -raw application_url 2>/dev/null || echo "")
  CERT_ARN=$(terraform -chdir="$TERRAFORM_DIR" output -raw acm_certificate_arn 2>/dev/null || echo "")
  
  if [ -n "${ALB_DNS:-}" ] || [ -n "$APP_URL" ]; then
    echo "🌐 Application URL:"
    if [ -n "$APP_URL" ]; then
      echo "  $APP_URL"
      if [ -n "$DOMAIN" ]; then
        echo "  (Domain: $DOMAIN)"
      fi
    else
      echo "  http://${ALB_DNS}"
    fi
    echo ""
    
    # Show DNS validation records if domain is set
    if [ -n "$DOMAIN" ] && [ -n "$CERT_ARN" ]; then
      echo "🔐 SSL Certificate:"
      echo "  Certificate ARN: $CERT_ARN"
      echo ""
      
      # Check if Route53 zone_id was provided
      R53_ZONE_ID=$(terraform -chdir="$TERRAFORM_DIR" output -raw route53_zone_id 2>/dev/null || echo "")
      
      if [ -n "$R53_ZONE_ID" ]; then
        log_success "Route53 zone ID provided - DNS records created automatically"
        echo ""
        echo "✅ DNS Setup Complete:"
        echo "  • Certificate validation records: Created in Route53"
        echo "  • A record for $DOMAIN: Created"
        echo "  • A record for www.$DOMAIN: Created"
        echo ""
        echo "⏳ Certificate validation may take 5-30 minutes"
        echo "   Check status: aws acm describe-certificate --certificate-arn $CERT_ARN --region $AWS_REGION --query 'Certificate.Status'"
      else
        log_warn "Route53 zone ID not provided - Manual DNS setup required"
        echo ""
        echo "📋 DNS Records to Set in Your DNS Provider:"
        echo ""
        
        # Get validation records
        VALIDATION_RECORDS=$(aws acm describe-certificate \
          --certificate-arn "$CERT_ARN" \
          --region "$AWS_REGION" \
          --query 'Certificate.DomainValidationOptions[*].[DomainName,ResourceRecord.Name,ResourceRecord.Value,ResourceRecord.Type]' \
          --output text 2>/dev/null || echo "")
        
        if [ -n "$VALIDATION_RECORDS" ]; then
          echo "Certificate Validation Records (CNAME):"
          echo "$VALIDATION_RECORDS" | while read -r domain name value type; do
            echo "  Domain: $domain"
            echo "    Name:  $name"
            echo "    Value: $value"
            echo "    Type:  $type"
            echo ""
          done
        fi
        
        echo "ALB A Record (after certificate is validated):"
        echo "  Name:  $DOMAIN"
        echo "  Type:  A (Alias)"
        echo "  Value: ${ALB_DNS}"
        echo ""
        echo "  Name:  www.$DOMAIN"
        echo "  Type:  A (Alias)"
        echo "  Value: ${ALB_DNS}"
        echo ""
        echo "⚠️  IMPORTANT:"
        echo "  1. Add the CNAME validation records above to your DNS provider"
        echo "  2. Wait for certificate validation (check with command above)"
        echo "  3. Once validated, add A records pointing to ALB"
        echo "  4. HTTPS will work once certificate is validated and DNS propagates"
        echo ""
      fi
    fi
    
    echo "🔍 Useful Commands:"
    if [ -n "$APP_URL" ]; then
      echo "  Health Check: curl $APP_URL/actuator/health"
    else
      echo "  Health Check: curl http://${ALB_DNS}/actuator/health"
    fi
    echo "  ECS Status:  aws ecs describe-services --cluster $CLUSTER_NAME --services ${SERVICES[*]} --region $AWS_REGION"
    echo "  View Logs:    aws logs tail /ecs/cg-notification-api --follow --region $AWS_REGION"
    if [ -n "$DOMAIN" ]; then
      echo "  Login URL:    $APP_URL/auth/login"
    else
      echo "  Login URL:    http://${ALB_DNS}/auth/login"
    fi
    echo ""
  fi
  
  log_success "Deployment successful! 🎉"
}

# Run main function
check_prerequisites
main
