#!/bin/bash
set -e

# Run database migrations using an ECS task (runs inside VPC, can access RDS Proxy)
# This uses the migration Docker image that contains all SQL files

export AWS_PROFILE=${AWS_PROFILE:-sourav-admin}
export AWS_REGION=${AWS_REGION:-ap-south-1}

CLUSTER_NAME="cg-notification-cluster"
TASK_DEFINITION_FAMILY="cg-notification-migration-task"

# Dynamically resolve values from Terraform/AWS (fresh-deploy safe)
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ECR_REPO="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/cg-notification/migration"

# Use RDS VPC subnets and ECS SG - migration task must run where RDS Proxy is reachable.
# RDS Proxy only allows connections from ecs_in_rds_vpc (ECS SG in RDS VPC).
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
SUBNET_IDS="$(terraform output -json migration_subnet_ids 2>/dev/null | jq -r 'join(",")' | sed 's/"//g')"
SECURITY_GROUP_ID="$(terraform output -raw migration_security_group_id 2>/dev/null)"
cd - >/dev/null
EXECUTION_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/cg-notification-ecs-task-execution-role"
RDS_PROXY_ENDPOINT="$(
  cd "$(dirname "$0")" && terraform output -raw rds_proxy_endpoint
)"
DB_PASSWORD_ARN="$(
  aws secretsmanager describe-secret \
    --secret-id "cg-notification/db-password-only" \
    --query 'ARN' \
    --output text \
    --region "$AWS_REGION"
)"

echo "=========================================="
echo "Running database migrations via ECS task"
echo "=========================================="
echo "Cluster: $CLUSTER_NAME"
echo "ECR Image: $ECR_REPO:latest"
echo "Subnets: $SUBNET_IDS"
echo "Security Group: $SECURITY_GROUP_ID"
echo "RDS Proxy: $RDS_PROXY_ENDPOINT"
echo ""

# Check if migration image exists in ECR
echo "Checking if migration image exists in ECR..."
if aws ecr describe-images --repository-name cg-notification/migration --image-ids imageTag=latest --region "$AWS_REGION" > /dev/null 2>&1; then
  echo "✓ Migration image found in ECR"
else
  echo "❌ ERROR: Migration image not found in ECR!"
  echo "   Repository: cg-notification/migration"
  echo "   Tag: latest"
  echo ""
  echo "   You need to build and push the migration image first:"
  echo "   1. Build: docker build -f deployment/Dockerfile.migration -t $ECR_REPO:latest ."
  echo "   2. Push:  docker push $ECR_REPO:latest"
  echo ""
  exit 1
fi
echo ""

# Migration order from docker-compose.yml (lines 13-30)
MIGRATIONS=(
  "init-db.sql"
  "migration-add-users.sql"
  "migration-add-wasender-config.sql"
  "migration-add-message-fields.sql"
  "migration-add-retrying-status.sql"
  "migration-add-failure-type.sql"
  "migration-add-failure-type-constraint.sql"
  "migration-add-message-status-history.sql"
  "migration-add-history-source-column.sql"
  "migration-add-status-history-trigger.sql"
  "migration-add-email-config.sql"
  "migration-add-whatsapp-session.sql"
  "migration-make-site-id-nullable.sql"
  "migration-add-whatsapp-sessions-table.sql"
  "migration-add-sendgrid-config.sql"
  "migration-add-sendgrid-email-config.sql"
  "migration-add-partial-unique-indexes.sql"
  "migration-skip-metrics-when-site-id-null.sql"
)

# Build migration command (use $VAR syntax for env vars, they'll be expanded by container shell)
# Note: $ signs don't need escaping in JSON strings, only backslashes and quotes do
MIGRATION_CMD="set -e && "
for migration in "${MIGRATIONS[@]}"; do
  MIGRATION_CMD="${MIGRATION_CMD}echo '========================================' && echo 'Running ${migration}...' && echo '========================================' && PGPASSWORD=\$DB_PASSWORD PGSSLMODE=require psql -h \$RDS_PROXY_ENDPOINT -U \$DB_USER -d \$DB_NAME -f /migrations/${migration} && echo '' && "
done
MIGRATION_CMD="${MIGRATION_CMD}echo '========================================' && echo 'All migrations completed successfully!' && echo '========================================'"

# Escape the command for JSON (only escape backslashes and quotes, NOT dollar signs)
MIGRATION_CMD_ESCAPED=$(echo "$MIGRATION_CMD" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')

echo "Creating migration task definition..."
cat > /tmp/migration-task-def.json <<EOF
{
  "family": "$TASK_DEFINITION_FAMILY",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "$EXECUTION_ROLE_ARN",
  "containerDefinitions": [{
    "name": "migration",
    "image": "$ECR_REPO:latest",
    "essential": true,
    "command": ["sh", "-c", "$MIGRATION_CMD_ESCAPED"],
    "environment": [
      {"name": "RDS_PROXY_ENDPOINT", "value": "$RDS_PROXY_ENDPOINT"},
      {"name": "DB_USER", "value": "notification_user"},
      {"name": "DB_NAME", "value": "notification_db"}
    ],
    "secrets": [
      {"name": "DB_PASSWORD", "valueFrom": "$DB_PASSWORD_ARN"}
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/migrations",
        "awslogs-region": "$AWS_REGION",
        "awslogs-stream-prefix": "migration"
      }
    }
  }]
}
EOF

# Create log group if it doesn't exist
aws logs create-log-group --log-group-name /ecs/migrations 2>/dev/null || true

# Register task definition
echo "Registering task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition --cli-input-json file:///tmp/migration-task-def.json --query 'taskDefinition.taskDefinitionArn' --output text)
echo "Task definition: $TASK_DEF_ARN"
echo ""

# Run the task
echo "Starting migration task..."
TASK_ARN=$(aws ecs run-task \
  --cluster $CLUSTER_NAME \
  --task-definition $TASK_DEF_ARN \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_IDS],securityGroups=[$SECURITY_GROUP_ID],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' \
  --output text)

echo "Task ARN: $TASK_ARN"
echo "Waiting for task to complete (this may take a few minutes)..."
echo ""

# Wait for task to stop
echo "Waiting for task to complete..."
aws ecs wait tasks-stopped --cluster $CLUSTER_NAME --tasks $TASK_ARN

# Get task details
TASK_DETAILS=$(aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks $TASK_ARN)
EXIT_CODE=$(echo "$TASK_DETAILS" | jq -r '.tasks[0].containers[0].exitCode // "null"')
STOPPED_REASON=$(echo "$TASK_DETAILS" | jq -r '.tasks[0].stoppedReason // "N/A"')
LAST_STATUS=$(echo "$TASK_DETAILS" | jq -r '.tasks[0].lastStatus // "N/A"')
DESIRED_STATUS=$(echo "$TASK_DETAILS" | jq -r '.tasks[0].desiredStatus // "N/A"')

echo ""
echo "=========================================="
echo "Task Status:"
echo "  Last Status: $LAST_STATUS"
echo "  Desired Status: $DESIRED_STATUS"
echo "  Exit Code: $EXIT_CODE"
echo "  Stopped Reason: $STOPPED_REASON"
echo "=========================================="
echo ""

# Wait for logs to appear (CloudWatch can be delayed)
echo "Waiting for logs to appear (may take 10-30 seconds)..."
sleep 15

# Get logs - use task-specific log stream (format: prefix/container/taskid)
TASK_ID=$(echo "$TASK_ARN" | rev | cut -d'/' -f1 | rev)
LOG_STREAM="migration/migration/$TASK_ID"
EVENTS=""

echo "Migration logs:"
echo "=========================================="
echo "Log stream: $LOG_STREAM"
echo ""

# Try to get logs (stream may take a moment to appear)
for i in 1 2 3 4 5; do
  EVENTS=$(aws logs get-log-events \
    --log-group-name /ecs/migrations \
    --log-stream-name "$LOG_STREAM" \
    --query 'events[*].message' \
    --output text 2>/dev/null || true)
  if [ -n "$EVENTS" ] && [ "$EVENTS" != "None" ]; then
    echo "$EVENTS"
    break
  fi
  if [ $i -lt 5 ]; then
    echo "  Waiting for logs... (attempt $i/5)"
    sleep 5
  else
    echo "Could not retrieve log events. Check CloudWatch: aws logs get-log-events --log-group-name /ecs/migrations --log-stream-name $LOG_STREAM --region $AWS_REGION"
  fi
done

if [ -z "$EVENTS" ] || [ "$EVENTS" = "None" ]; then
  echo "⚠️  No log stream found after waiting"
  echo ""
  echo "Checking task details for errors..."
  # Check for container errors
  CONTAINER_REASON=$(echo "$TASK_DETAILS" | jq -r '.tasks[0].containers[0].reason // "N/A"')
  echo "  Container reason: $CONTAINER_REASON"
  
  # List all log streams in the group
  echo ""
  echo "Available log streams in /ecs/migrations:"
  aws logs describe-log-streams \
    --log-group-name /ecs/migrations \
    --max-items 5 \
    --query 'logStreams[*].logStreamName' \
    --output table 2>/dev/null || echo "  Could not list log streams"
fi

echo ""
echo "=========================================="

# Handle exit code - None/null means task didn't complete properly
if [ "$EXIT_CODE" = "null" ] || [ "$EXIT_CODE" = "None" ] || [ -z "$EXIT_CODE" ]; then
  echo "❌ Task did not complete properly (exit code: $EXIT_CODE)"
  echo "   Stopped reason: $STOPPED_REASON"
  echo ""
  echo "Common causes:"
  echo "  1. Migration Docker image not found in ECR"
  echo "  2. Task failed to start (check IAM permissions)"
  echo "  3. Network connectivity issues"
  echo "  4. Container crashed immediately"
  echo ""
  echo "To debug:"
  echo "  1. Check if migration image exists:"
  echo "     aws ecr describe-images --repository-name cg-notification/migration --region $AWS_REGION"
  echo "  2. Check ECS task events:"
  echo "     aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks $TASK_ARN"
  echo "  3. Check CloudWatch logs manually:"
  echo "     aws logs tail /ecs/migrations --follow --region $AWS_REGION"
  exit 1
elif [ "$EXIT_CODE" = "0" ]; then
  echo "✅ Migrations completed successfully!"
  echo ""
  echo "Verifying tables..."
  # Run a quick verification task
  VERIFY_CMD="PGPASSWORD=\$DB_PASSWORD PGSSLMODE=require psql -h \$RDS_PROXY_ENDPOINT -U \$DB_USER -d \$DB_NAME -c '\\\\dt'"
  VERIFY_CMD_ESCAPED=$(echo "$VERIFY_CMD" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
  VERIFY_TASK_ARN=$(aws ecs run-task \
    --cluster $CLUSTER_NAME \
    --task-definition $TASK_DEF_ARN \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_IDS],securityGroups=[$SECURITY_GROUP_ID],assignPublicIp=DISABLED}" \
    --overrides "containerOverrides=[{name=migration,command=[sh,-c,\"$VERIFY_CMD_ESCAPED\"]}]" \
    --query 'tasks[0].taskArn' \
    --output text)
  aws ecs wait tasks-stopped --cluster $CLUSTER_NAME --tasks $VERIFY_TASK_ARN
  echo ""
  echo "Tables in database:"
  VERIFY_LOG_STREAM=$(aws logs describe-log-streams --log-group-name /ecs/migrations --order-by LastEventTime --descending --max-items 1 --query 'logStreams[0].logStreamName' --output text)
  if [ -n "$VERIFY_LOG_STREAM" ] && [ "$VERIFY_LOG_STREAM" != "None" ]; then
    aws logs get-log-events --log-group-name /ecs/migrations --log-stream-name "$VERIFY_LOG_STREAM" --query 'events[*].message' --output text | grep -A 50 "public" || echo "Check logs manually"
  fi
  exit 0
else
  echo "❌ Migrations failed with exit code $EXIT_CODE"
  echo "Check the logs above for details"
  exit 1
fi
