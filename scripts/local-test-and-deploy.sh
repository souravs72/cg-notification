#!/bin/bash
# Complete workflow: Test locally, then deploy to AWS
# This script:
# 1. Starts local Docker Compose (ECS-like setup)
# 2. Tests health and liveness probes
# 3. On success, deploys to AWS
# 4. On failure, shows errors and exits

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "=========================================="
echo "Local Test and AWS Deployment Workflow"
echo "=========================================="
echo ""

# Step 1: Start local Docker Compose
echo "Step 1: Starting local Docker Compose (ECS-like setup)..."
echo ""

docker-compose -f docker-compose.ecs-test.yml up -d --build

echo ""
echo "Waiting for services to be ready..."
sleep 10

# Step 2: Test health probes
echo ""
echo "Step 2: Testing health and liveness probes..."
echo ""

if "$SCRIPT_DIR/test-health-probes.sh"; then
    echo ""
    echo "=========================================="
    echo "✓ Local tests passed!"
    echo "=========================================="
    echo ""
    
    # Step 3: Deploy to AWS
    read -p "Deploy to AWS? (y/N): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "Step 3: Deploying to AWS..."
        echo ""
        "$SCRIPT_DIR/deploy-to-aws.sh"
        
        echo ""
        echo "=========================================="
        echo "✓ Deployment complete!"
        echo "=========================================="
        echo ""
        echo "Next steps:"
        echo "  1. Monitor ECS services:"
        echo "     aws ecs describe-services --cluster cg-notification-cluster --services notification-api-service email-worker-service whatsapp-worker-service --region ap-south-1"
        echo ""
        echo "  2. Check service logs:"
        echo "     aws logs tail /ecs/cg-notification-api --follow --region ap-south-1"
        echo ""
        echo "  3. Test health endpoint:"
        echo "     curl http://\$(terraform -chdir=terraform output -raw alb_dns_name)/actuator/health"
    else
        echo "Skipping AWS deployment."
    fi
else
    echo ""
    echo "=========================================="
    echo "✗ Local tests failed!"
    echo "=========================================="
    echo ""
    echo "Please fix the issues before deploying to AWS."
    echo ""
    echo "Check service logs:"
    echo "  docker-compose -f docker-compose.ecs-test.yml logs notification-api"
    echo "  docker-compose -f docker-compose.ecs-test.yml logs email-worker"
    echo "  docker-compose -f docker-compose.ecs-test.yml logs whatsapp-worker"
    echo ""
    echo "Stop services:"
    echo "  docker-compose -f docker-compose.ecs-test.yml down"
    exit 1
fi
