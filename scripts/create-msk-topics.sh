#!/usr/bin/env bash
# Create MSK topics (notifications-email, notifications-whatsapp) via a one-off ECS task.
# Run from project root. Requires: docker, aws CLI, jq, terraform (for outputs).
# The task uses the kafka-admin image (Kafka tools + aws-msk-iam-auth) and the API task role (MSK access).

set -e

export AWS_PROFILE="${AWS_PROFILE:-sourav-admin}"
export AWS_REGION="${AWS_REGION:-ap-south-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="${TERRAFORM_DIR:-$PROJECT_ROOT/terraform}"

CLUSTER_NAME="cg-notification-cluster"
TASK_DEF_FAMILY="cg-notification-kafka-admin"
LOG_GROUP="/ecs/kafka-admin"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ECR_REPO="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/cg-notification/kafka-admin"

# Use same VPC as MSK cluster (discover from RDS - MSK/ECS/RDS typically share VPC)
RDS_VPC_ID="$(aws rds describe-db-instances --db-instance-identifier cg-notification-db --region "$AWS_REGION" --query 'DBInstances[0].DBSubnetGroup.VpcId' --output text 2>/dev/null)"
if [ -n "$RDS_VPC_ID" ] && [ "$RDS_VPC_ID" != "None" ]; then
  SUBNET_IDS="$(aws rds describe-db-subnet-groups --db-subnet-group-name cg-notification-db-subnet --region "$AWS_REGION" --query 'DBSubnetGroups[0].Subnets[*].SubnetIdentifier' --output text 2>/dev/null | tr '\t' ',')"
  SG_ID="$(aws ec2 describe-security-groups --filters "Name=tag:Name,Values=cg-notification-ecs-sg" "Name=vpc-id,Values=$RDS_VPC_ID" --query 'SecurityGroups[0].GroupId' --output text --region "$AWS_REGION" 2>/dev/null)"
fi
if [ -z "$SUBNET_IDS" ] || [ -z "$SG_ID" ] || [ "$SG_ID" = "None" ]; then
  SUBNET_IDS="$(cd "$TERRAFORM_DIR" && terraform output -json private_subnet_ids | jq -r 'join(",")')"
  SG_ID="$(cd "$TERRAFORM_DIR" && terraform output -raw ecs_security_group_id 2>/dev/null)"
fi
BOOTSTRAP="$(cd "$TERRAFORM_DIR" && terraform output -raw msk_bootstrap_brokers)"
EXECUTION_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/cg-notification-ecs-task-execution-role"
TASK_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/cg-api-task-role"

CREATE_CMD='set -e;
  echo "security.protocol=SASL_SSL" > /tmp/client.properties;
  echo "sasl.mechanism=AWS_MSK_IAM" >> /tmp/client.properties;
  echo "sasl.jaas.config=software.amazon.msk.auth.iam.IAMLoginModule required;" >> /tmp/client.properties;
  echo "sasl.client.callback.handler.class=software.amazon.msk.auth.iam.IAMClientCallbackHandler" >> /tmp/client.properties;
  for t in notifications-email notifications-whatsapp; do
    kafka-topics --bootstrap-server "$MSK_BOOTSTRAP" --create --topic "$t" --partitions 6 --command-config /tmp/client.properties || true;
  done;
  echo "Done creating topics."'

CREATE_CMD_ESCAPED="$(printf '%s' "$CREATE_CMD" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | tr '\n' ' ')"

echo "=========================================="
echo "Create MSK topics via ECS task"
echo "=========================================="
echo "Cluster: $CLUSTER_NAME"
echo "Bootstrap: ${BOOTSTRAP:0:50}..."
echo ""

# Build and push kafka-admin image
echo "Building kafka-admin image..."
docker build -f "$PROJECT_ROOT/deployment/Dockerfile.kafka-admin" -t "$ECR_REPO:latest" "$PROJECT_ROOT"
# Login may fail with "error storing credentials - not implemented" when using ECR credential helper;
# that's fine — push uses the helper for auth. Avoid exiting on login failure.
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com" || true
echo "Pushing to ECR..."
docker push "$ECR_REPO:latest"

# Ensure log group
aws logs create-log-group --log-group-name "$LOG_GROUP" 2>/dev/null || true

# Register task definition
echo "Registering task definition..."
cat > /tmp/kafka-admin-task.json <<EOF
{
  "family": "$TASK_DEF_FAMILY",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "$EXECUTION_ROLE_ARN",
  "taskRoleArn": "$TASK_ROLE_ARN",
  "containerDefinitions": [{
    "name": "kafka-admin",
    "image": "$ECR_REPO:latest",
    "essential": true,
    "command": ["sh", "-c", "$CREATE_CMD_ESCAPED"],
    "environment": [{"name": "MSK_BOOTSTRAP", "value": "$BOOTSTRAP"}],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "$LOG_GROUP",
        "awslogs-region": "$AWS_REGION",
        "awslogs-stream-prefix": "ecs"
      }
    }
  }]
}
EOF

TASK_DEF_ARN="$(aws ecs register-task-definition --cli-input-json file:///tmp/kafka-admin-task.json --query 'taskDefinition.taskDefinitionArn' --output text)"

# Run task
echo "Starting one-off task..."
TASK_ARN="$(aws ecs run-task \
  --cluster "$CLUSTER_NAME" \
  --task-definition "$TASK_DEF_ARN" \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_IDS],securityGroups=[$SG_ID],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' \
  --output text)"

echo "Task: $TASK_ARN"
echo "Waiting for task to finish..."
aws ecs wait tasks-stopped --cluster "$CLUSTER_NAME" --tasks "$TASK_ARN"

EXIT="$(aws ecs describe-tasks --cluster "$CLUSTER_NAME" --tasks "$TASK_ARN" --query 'tasks[0].containers[0].exitCode' --output text)"
echo "Exit code: $EXIT"

LOG_STREAM="$(aws logs describe-log-streams --log-group-name "$LOG_GROUP" --order-by LastEventTime --descending --max-items 1 --query 'logStreams[0].logStreamName' --output text)"
echo "Recent logs:"
aws logs get-log-events --log-group-name "$LOG_GROUP" --log-stream-name "$LOG_STREAM" --query 'events[*].message' --output text 2>/dev/null || true

if [ "$EXIT" != "0" ] && [ "$EXIT" != "null" ]; then
  echo "Task failed. Check CloudWatch log group: $LOG_GROUP"
  exit 1
fi
echo "Topics created successfully."
