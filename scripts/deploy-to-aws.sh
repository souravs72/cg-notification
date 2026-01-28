#!/bin/bash
# Deploy to AWS after successful local testing
# This script builds images, pushes to ECR, and triggers ECS service updates

set -e

# CRITICAL: Set AWS profile and region
export AWS_PROFILE=${AWS_PROFILE:-sourav-admin}
export AWS_REGION=${AWS_REGION:-ap-south-1}
# Set IMAGE_TAG for rollback-safe deploys (e.g. $(git rev-parse --short HEAD)). Default: latest.
export IMAGE_TAG=${IMAGE_TAG:-latest}

echo "=========================================="
echo "Deploying to AWS"
echo "=========================================="
echo ""

# Verify AWS identity
echo "Verifying AWS identity..."
if ! aws sts get-caller-identity > /dev/null 2>&1; then
  echo "❌ ERROR: AWS credentials not configured or invalid"
  echo "   Ensure AWS_PROFILE is exported or AWS credentials are configured"
  exit 1
fi

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Using AWS account: $ACCOUNT_ID"
echo "Using region: $AWS_REGION"
echo ""

# Get ECR repository URLs from Terraform
cd terraform

if [ ! -f terraform.tfstate ]; then
    echo "❌ ERROR: Terraform state not found. Run 'terraform apply' first."
    exit 1
fi

ECR_API_URL=$(terraform output -raw ecr_api_repository_url 2>/dev/null || echo "")
ECR_EMAIL_URL=$(terraform output -raw ecr_email_worker_repository_url 2>/dev/null || echo "")
ECR_WHATSAPP_URL=$(terraform output -raw ecr_whatsapp_worker_repository_url 2>/dev/null || echo "")

if [ -z "$ECR_API_URL" ] || [ -z "$ECR_EMAIL_URL" ] || [ -z "$ECR_WHATSAPP_URL" ]; then
    echo "❌ ERROR: Could not get ECR repository URLs from Terraform outputs"
    echo "   Run 'terraform apply' first to create ECR repositories"
    exit 1
fi

cd ..

echo "ECR Repositories:"
echo "  API: $ECR_API_URL"
echo "  Email Worker: $ECR_EMAIL_URL"
echo "  WhatsApp Worker: $ECR_WHATSAPP_URL"
echo "Image tag: $IMAGE_TAG"
echo ""

# Login to ECR
echo "Logging in to ECR..."
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  ${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

echo ""

# Build and push images
echo "=========================================="
echo "Building and pushing Docker images"
echo "=========================================="
echo ""

# Build API image
echo "Building notification-api..."
docker build -f notification-api/Dockerfile -t ${ECR_API_URL}:${IMAGE_TAG} .
docker tag ${ECR_API_URL}:${IMAGE_TAG} ${ECR_API_URL}:latest
echo "Pushing notification-api..."
docker push ${ECR_API_URL}:${IMAGE_TAG}
docker push ${ECR_API_URL}:latest
echo "✓ notification-api pushed"
echo ""

# Build Email Worker image
echo "Building email-worker..."
docker build -f email-worker/Dockerfile -t ${ECR_EMAIL_URL}:${IMAGE_TAG} .
docker tag ${ECR_EMAIL_URL}:${IMAGE_TAG} ${ECR_EMAIL_URL}:latest
echo "Pushing email-worker..."
docker push ${ECR_EMAIL_URL}:${IMAGE_TAG}
docker push ${ECR_EMAIL_URL}:latest
echo "✓ email-worker pushed"
echo ""

# Build WhatsApp Worker image
echo "Building whatsapp-worker..."
docker build -f whatsapp-worker/Dockerfile -t ${ECR_WHATSAPP_URL}:${IMAGE_TAG} .
docker tag ${ECR_WHATSAPP_URL}:${IMAGE_TAG} ${ECR_WHATSAPP_URL}:latest
echo "Pushing whatsapp-worker..."
docker push ${ECR_WHATSAPP_URL}:${IMAGE_TAG}
docker push ${ECR_WHATSAPP_URL}:latest
echo "✓ whatsapp-worker pushed"
echo ""

# Update ECS task definitions to use IMAGE_TAG (then force deploy)
if [ "$IMAGE_TAG" != "latest" ]; then
  echo "=========================================="
  echo "Updating task definitions (image_tag=$IMAGE_TAG)"
  echo "=========================================="
  cd terraform
  terraform apply -var="image_tag=$IMAGE_TAG" \
    -target=aws_ecs_task_definition.api \
    -target=aws_ecs_task_definition.email_worker \
    -target=aws_ecs_task_definition.whatsapp_worker \
    -target=aws_ecs_service.api \
    -target=aws_ecs_service.email_worker \
    -target=aws_ecs_service.whatsapp_worker \
    -auto-approve -input=false
  cd ..
  echo ""
fi

# Force ECS service deployment
echo "=========================================="
echo "Triggering ECS service updates"
echo "=========================================="
echo ""

CLUSTER_NAME="cg-notification-cluster"

echo "Updating notification-api-service..."
aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service notification-api-service \
  --force-new-deployment \
  --region $AWS_REGION \
  --query 'service.[serviceName,status,runningCount,desiredCount]' \
  --output table

echo ""
echo "Updating email-worker-service..."
aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service email-worker-service \
  --force-new-deployment \
  --region $AWS_REGION \
  --query 'service.[serviceName,status,runningCount,desiredCount]' \
  --output table

echo ""
echo "Updating whatsapp-worker-service..."
aws ecs update-service \
  --cluster $CLUSTER_NAME \
  --service whatsapp-worker-service \
  --force-new-deployment \
  --region $AWS_REGION \
  --query 'service.[serviceName,status,runningCount,desiredCount]' \
  --output table

echo ""
echo "=========================================="
echo "Deployment initiated!"
echo "=========================================="
echo ""
echo "Services are being updated. This may take 5-10 minutes."
echo ""
echo "Monitor deployment:"
echo "  aws ecs describe-services \\"
echo "    --cluster $CLUSTER_NAME \\"
echo "    --services notification-api-service email-worker-service whatsapp-worker-service \\"
echo "    --region $AWS_REGION \\"
echo "    --query 'services[*].[serviceName,runningCount,desiredCount,status]' \\"
echo "    --output table"
echo ""
echo "Check service logs:"
echo "  aws logs tail /ecs/cg-notification-api --follow --region $AWS_REGION"
echo ""
echo "Test health endpoint:"
echo "  curl http://\$(terraform -chdir=terraform output -raw alb_dns_name)/actuator/health"
