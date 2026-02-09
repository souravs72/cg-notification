#!/usr/bin/env bash
# Check DB connectivity from ECS perspective (VPC, security groups, endpoint reachability).
# Run from project root. Requires: aws CLI, jq.

set -e

export AWS_PROFILE="${AWS_PROFILE:-sourav-admin}"
export AWS_REGION="${AWS_REGION:-ap-south-1}"

echo "=== DB connectivity check ==="
echo ""

# RDS Proxy endpoint
PROXY_ENDPOINT="cg-notification-db-proxy.proxy-cx6k62o2qqat.ap-south-1.rds.amazonaws.com"
RDS_SUBNET_VPC=$(aws rds describe-db-instances --db-instance-identifier cg-notification-db --region "$AWS_REGION" --query 'DBInstances[0].DBSubnetGroup.VpcId' --output text 2>/dev/null || echo "N/A")
PROXY_SGS=$(aws rds describe-db-proxies --db-proxy-name cg-notification-db-proxy --region "$AWS_REGION" --query 'DBProxies[0].VpcSecurityGroupIds' --output text 2>/dev/null || echo "N/A")
PROXY_SG_FIRST=$(echo "$PROXY_SGS" | awk '{print $1}')
[ -n "$PROXY_SG_FIRST" ] && PROXY_VPC=$(aws ec2 describe-security-groups --group-ids "$PROXY_SG_FIRST" --region "$AWS_REGION" --query 'SecurityGroups[0].VpcId' --output text 2>/dev/null) || PROXY_VPC="N/A"

# ECS service network config (from running tasks)
TASK_ARN=$(aws ecs list-tasks --cluster cg-notification-cluster --service-name notification-api-service --region "$AWS_REGION" --query 'taskArns[0]' --output text 2>/dev/null)
if [ -n "$TASK_ARN" ] && [ "$TASK_ARN" != "None" ]; then
  ENI_ID=$(aws ecs describe-tasks --cluster cg-notification-cluster --tasks "$TASK_ARN" --region "$AWS_REGION" --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text 2>/dev/null)
  if [ -n "$ENI_ID" ]; then
    ECS_VPC=$(aws ec2 describe-network-interfaces --network-interface-ids "$ENI_ID" --region "$AWS_REGION" --query 'NetworkInterfaces[0].VpcId' --output text 2>/dev/null)
    ECS_SG=$(aws ec2 describe-network-interfaces --network-interface-ids "$ENI_ID" --region "$AWS_REGION" --query 'NetworkInterfaces[0].Groups[0].GroupId' --output text 2>/dev/null)
  else
    ECS_VPC="N/A (no running task)"
    ECS_SG="N/A"
  fi
else
  ECS_VPC="N/A (no running task)"
  ECS_SG="N/A"
fi

echo "RDS instance VPC:     $RDS_SUBNET_VPC"
echo "RDS Proxy VPC:        $PROXY_VPC"
echo "RDS Proxy SG(s):      $PROXY_SGS"
echo "ECS task VPC:         $ECS_VPC"
echo "ECS task SG:          $ECS_SG"
echo "Proxy endpoint:       $PROXY_ENDPOINT"
echo ""

if [ "$RDS_SUBNET_VPC" != "$ECS_VPC" ] || [ "$PROXY_VPC" != "$ECS_VPC" ]; then
  echo "⚠ VPC mismatch: ECS tasks must be in same VPC as RDS/RDS Proxy."
  echo "  Fix: Ensure ECS service network_configuration uses subnets in $RDS_SUBNET_VPC"
fi

# Check if RDS Proxy SG allows ECS SG
if [ -n "$PROXY_SG_FIRST" ] && [ -n "$ECS_SG" ] && [ "$ECS_SG" != "None" ]; then
  ALLOWED=$(aws ec2 describe-security-groups --group-ids "$PROXY_SG_FIRST" --region "$AWS_REGION" \
    --query "SecurityGroups[0].IpPermissions[?FromPort==\`5432\` && ToPort==\`5432\`].UserIdGroupPairs[?GroupId==\`$ECS_SG\`]" --output text 2>/dev/null)
  if [ -z "$ALLOWED" ]; then
    echo ""
    echo "⚠ RDS Proxy SG does not allow inbound 5432 from ECS SG ($ECS_SG)."
    echo "  Fix: Add ingress rule to RDS Proxy SG: allow TCP 5432 from $ECS_SG"
    echo "  AWS CLI: aws ec2 authorize-security-group-ingress --group-id <PROXY_SG_ID> --protocol tcp --port 5432 --source-group $ECS_SG --region $AWS_REGION"
  else
    echo "✓ RDS Proxy SG allows 5432 from ECS SG"
  fi
fi

# Secret check
echo ""
echo "Checking db_password_only secret exists..."
aws secretsmanager get-secret-value --secret-id cg-notification/db-password-only --region "$AWS_REGION" --query 'SecretString' --output text 2>/dev/null | head -c 5
echo "... (redacted)"
