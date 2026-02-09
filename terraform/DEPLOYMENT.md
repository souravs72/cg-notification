# Terraform Deployment Guide

Deploy **ALB → ECS Fargate → RDS + RDS Proxy → MSK Serverless → S3 + KMS → Secrets Manager** using only AWS CLI, Terraform CLI, and environment variables. Secret values are injected manually (or via script).

**Quick deploy (after prerequisites):**
```bash
export AWS_PROFILE=<profile>
export AWS_REGION=ap-south-1
aws sts get-caller-identity   # verify identity
cd terraform
terraform init && terraform plan -out=tfplan && terraform apply tfplan
```
Then: inject secrets → run migrations (`./run-migrations-ecs.sh`) → create MSK topics (`./scripts/create-msk-topics.sh`) → build/push images → force ECS deploy. See below for details.

**Full automation (single script):**
`./scripts/deploy-trigger.sh` runs: Terraform plan → apply → inject secrets → migrations → MSK topics → build/push images → force ECS deploy → wait for health. Set `WASENDER_API_KEY`, `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, `SENDGRID_FROM_NAME`, `ADMIN_API_KEY` first, or use `--skip-secrets` when secrets are already injected. Use `--skip-terraform` for app-only (migrate, topics, build, ECS). Supports `--domain` and `--route53-zone-id` for custom domain setup.

**Manual DNS (Namecheap/Cloudflare/etc):** If you pass `--domain` without `--route53-zone-id`, ACM DNS validation must be done manually. After Terraform runs, get the required CNAMEs via:

```bash
cd terraform
terraform output acm_certificate_validation_records
```

---

## Prerequisites

### Required Tools

- **AWS CLI** (v2.x recommended)
  - Verify: `aws --version`
  - Configure: `aws configure` or set `AWS_PROFILE` environment variable

- **Terraform** (>= 1.0)
  - Verify: `terraform version`
  - Download: https://www.terraform.io/downloads

- **PostgreSQL Client** (for database migrations)
  - Verify: `psql --version`
  - Required for: Running database migrations via `null_resource` provisioner
  - Install: `sudo apt-get install postgresql-client` (Ubuntu/Debian) or `brew install postgresql` (macOS)

### AWS Account Setup

- AWS account with appropriate permissions
- IAM user/role with permissions to create:
  - VPC, subnets, NAT Gateway, Internet Gateway
  - ECS, ECR, RDS, RDS Proxy, MSK, ALB, S3
  - IAM roles and policies
  - Secrets Manager secrets
  - CloudWatch log groups
  - KMS keys
  - WAF

---

## ⚠️ CRITICAL: Set AWS Profile and Region

**ALWAYS export these environment variables FIRST** to ensure all commands use the same AWS account and region:

```bash
export AWS_PROFILE=<your-profile-name>  # e.g., sourav-admin
export AWS_REGION=ap-south-1
```

**Why this matters:**
- Terraform reads `AWS_PROFILE` environment variable
- AWS CLI defaults to `default` profile if not specified
- Without explicit profile, you may silently deploy to the wrong account
- Exporting once ensures Terraform, AWS CLI, and all scripts use the same identity

**Verify your identity:**
```bash
aws sts get-caller-identity
# Should show: Account: <account-id>, User: <user-name>
```

**Expected output:**
```json
{
    "UserId": "AIDA...",
    "Account": "012170751628",
    "Arn": "arn:aws:iam::012170751628:user/sourav-admin"
}
```

---

## Step-by-Step Deployment

### Step 0: Set AWS Profile and Region (REQUIRED)

```bash
export AWS_PROFILE=sourav-admin  # Replace with your profile name
export AWS_REGION=ap-south-1
```

**Explanation**: Ensures all subsequent commands (Terraform, AWS CLI, scripts) use the same AWS account and region.

### Step 1: Navigate to Terraform Directory

From the **project root** (where `pom.xml` and `terraform/` live):

```bash
cd terraform
# Or, if running from elsewhere: cd /path/to/cg-notification/terraform
```

### Step 2: Review and Create terraform.tfvars

**Create `terraform.tfvars`** (this file is git-ignored):

```bash
cp terraform.tfvars.example terraform.tfvars
```

**Edit `terraform.tfvars`** with your values:

```hcl
# AWS Configuration
aws_region  = "ap-south-1"
environment = "prod"

# VPC Configuration
vpc_cidr           = "10.0.0.0/16"
availability_zones = ["ap-south-1a", "ap-south-1b"]

# ECS Service Desired Counts
notification_api_desired_count = 2
email_worker_desired_count     = 2
whatsapp_worker_desired_count  = 2

# RDS Configuration
# Note: db.t3.micro is too small for production
rds_instance_class     = "db.t3.small"
rds_allocated_storage  = 20
rds_engine_version     = "15.15"  # Latest 15.x available in ap-south-1
rds_db_name            = "notification_db"
rds_master_username    = "notification_user"

# S3 Configuration
# IMPORTANT: S3 bucket names must be globally unique
# Suggested: add region + account hint
s3_bucket_name = "cg-notification-uploads-ap-south-1-UNIQUE-SUFFIX"

# MSK Configuration
msk_cluster_name = "cg-notification-msk"

# ECS Configuration
ecs_cluster_name = "cg-notification-cluster"

# ALB Configuration
alb_name                = "cg-notification-alb"
alb_deletion_protection = false  # Set to true for production

# Application Configuration
# Base URL for file uploads (used in API service)
# If not provided, will use ALB DNS name
file_upload_base_url = ""  # Leave empty to use ALB DNS name

# CloudWatch Configuration
# Log retention in days (valid values: 1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653)
cloudwatch_log_retention_days = 14

# ACM Certificate (optional - for HTTPS)
# If provided, HTTP will redirect to HTTPS
# acm_certificate_arn = "arn:aws:acm:ap-south-1:ACCOUNT:certificate/CERT-ID"
```

**Critical variables:**
- `s3_bucket_name`: Must be globally unique. If empty, Terraform will generate one.
- `rds_instance_class`: Minimum `db.t3.small` for production (micro is too small).
- `rds_engine_version`: Use `15.15` (latest 15.x available in ap-south-1).

### Step 3: Initialize Terraform

```bash
terraform init
```

**Expected output:**
```
Initializing the backend...
Initializing provider plugins...
- Finding hashicorp/aws versions matching "~> 5.0"...
- Finding hashicorp/random versions matching "~> 3.1"...
- Finding hashicorp/null versions matching "~> 3.0"...
...
Terraform has been successfully initialized!
```

**Explanation**: Downloads required providers (AWS, random, null) and initializes the backend.

**If you see errors:**
- `Error: No valid credential sources found`: Check `AWS_PROFILE` is exported and credentials are configured
- `Error: Failed to get existing workspaces`: Check AWS permissions

### Step 4: Validate Configuration

```bash
terraform validate
```

**Expected output:**
```
Success! The configuration is valid.
```

**Explanation**: Checks Terraform syntax and configuration validity without connecting to AWS.

**If validation fails:**
- Fix syntax errors shown in output
- Check variable types match expected values
- Verify all required variables are set

### Step 5: Generate Execution Plan

```bash
terraform plan -out=tfplan
```

**Expected output:**
```
Plan: X to add, 0 to change, 0 to destroy.
```

**Explanation**:
- Shows what resources will be created/modified/destroyed
- Saves plan to `tfplan` file for safe apply
- Review the plan carefully before proceeding
- **First deployment**: Expect ~50-60 resources to be created

**Review the plan for:**
- Correct resource names and tags
- Expected resource counts (ECS services, subnets, etc.)
- No unexpected deletions

**If plan shows errors:**
- `Error: InvalidParameterValue: The requested instance class db.t3.micro is not available`: Use `db.t3.small` or larger
- `Error: InvalidParameterValue: The requested engine version 15.4 is not available`: Use `15.15`
- `Error: BucketAlreadyExists`: Change `s3_bucket_name` to a unique value

### Step 6: Apply Infrastructure (First Time)

```bash
terraform apply tfplan
```

**Expected output:**
```
Apply complete! Resources: X added, 0 changed, 0 destroyed.
```

**Explanation**:
- Creates all AWS resources (VPC, RDS, ECS, MSK, etc.)
- Takes **15-30 minutes** (RDS and MSK take longest)
- **IMPORTANT**: This creates secret containers but NOT secret values
- **Database migrations**: If `enable_db_migrations = true` in `terraform.tfvars`, migrations run via `null_resource` (NOT recommended for production). Otherwise, run migrations separately using `./run-migrations-ecs.sh` (see Step 6.5)

**Timeline:**
- VPC, subnets, NAT Gateway: ~2-3 minutes
- RDS instance (Multi-AZ): ~10-15 minutes
- MSK Serverless cluster: ~5-10 minutes
- ECS cluster, task definitions: ~1-2 minutes
- ALB, target groups: ~2-3 minutes
- Total: ~20-30 minutes

**If apply fails:**
- See [Troubleshooting](#troubleshooting) section below
- Common issues: Insufficient permissions, resource limits, invalid parameters

**After successful apply:**
- Secret containers exist in Secrets Manager
- Secret values must be injected manually (security best practice)
- **ECS services may enter a restart loop until secrets are injected. This is expected behavior. Do not panic.** The services will automatically start successfully once secrets are injected in Step 7.

### Step 6.5: Run Database Migrations (REQUIRED)

**⚠️ CRITICAL**: Database migrations must be run before ECS services start. The `null_resource` approach in Terraform is **NOT recommended for production** (see warnings below).

**Recommended Approach: ECS Task-Based Migrations**

Run migrations using an ECS task (same network, same secrets, works in CI):

```bash
cd terraform
./run-migrations-ecs.sh
```

This script:
- Creates a one-off ECS task with the same image as your application
- Runs inside the VPC (can access RDS Proxy)
- Uses the same secrets and IAM roles
- Works in CI/CD pipelines
- Is idempotent and safe for production

**Alternative: null_resource Migrations (Development Only)**

⚠️ **WARNING**: Only use this for development/testing. Do NOT rely on null_resource migrations in:
- CI/CD pipelines (no local psql)
- Multi-operator environments (race conditions)
- Production environments (non-idempotent, tightly couples infra with schema)

If you must use null_resource migrations:

1. Set `enable_db_migrations = true` in `terraform.tfvars`
2. Ensure `psql` is installed locally: `psql --version`
3. Re-run `terraform apply`

**Why ECS Task Migrations Are Better:**
- ✅ Run from same network as application
- ✅ Use same secrets and IAM roles
- ✅ Work in CI/CD pipelines
- ✅ Idempotent and safe for retries
- ✅ Decouple infrastructure from schema state

### Step 6.6: Create MSK Topics (Required Before Sending Messages)

The API publishes to `notifications-email` and `notifications-whatsapp`. Those topics must exist on MSK. Create them once:

```bash
# From project root (parent of terraform/)
./scripts/create-msk-topics.sh
```

Requires Docker, AWS CLI, `jq`, and `terraform output` (run from `terraform/` or set `TERRAFORM_DIR`). The script builds a kafka-admin image, pushes to ECR, runs a one-off ECS task that creates the topics. Idempotent if topics already exist.

### Step 7: Inject Secret Values

After first `terraform apply` completes, inject secret values. **Terraform creates only the secret containers; you must provide values.**

**Option A: Script with env vars (recommended)**

```bash
cd terraform  # if not already there
export WASENDER_API_KEY="your-wasender-key"
export SENDGRID_API_KEY="your-sendgrid-key"
export SENDGRID_FROM_EMAIL="noreply@example.com"
export SENDGRID_FROM_NAME="Notification Service"
export ADMIN_API_KEY="your-admin-api-key"
./inject-secrets.sh
```

**Option B: Manual AWS CLI**

```bash
aws secretsmanager put-secret-value --secret-id cg-notification/wasender-api-key   --secret-string "YOUR-WASENDER-API-KEY"   --region $AWS_REGION
aws secretsmanager put-secret-value --secret-id cg-notification/sendgrid-api-key   --secret-string "YOUR-SENDGRID-API-KEY"   --region $AWS_REGION
aws secretsmanager put-secret-value --secret-id cg-notification/sendgrid-from-email --secret-string "noreply@example.com"     --region $AWS_REGION
aws secretsmanager put-secret-value --secret-id cg-notification/sendgrid-from-name  --secret-string "Notification Service"   --region $AWS_REGION
aws secretsmanager put-secret-value --secret-id cg-notification/admin-api-key      --secret-string "YOUR-ADMIN-API-KEY"     --region $AWS_REGION
```

**Verify:**
```bash
aws secretsmanager list-secrets --query 'SecretList[?contains(Name, `cg-notification`)].Name' --output table --region $AWS_REGION
```

ECS tasks may restart until secrets exist; once injected, services come up.

### Step 8: Build and Push Docker Images

**Get ECR URLs:** `terraform output ecr_api_repository_url` (and email-worker, whatsapp-worker).

**Login to ECR:**
```bash
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  $(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com
```
If you use the ECR credential helper, login may warn "error storing credentials"; push still works.

**Build and push (from project root):**
```bash
cd /path/to/cg-notification   # project root

API_URL=$(terraform -chdir=terraform output -raw ecr_api_repository_url)
EMAIL_URL=$(terraform -chdir=terraform output -raw ecr_email_worker_repository_url)
WA_URL=$(terraform -chdir=terraform output -raw ecr_whatsapp_worker_repository_url)

docker build -f notification-api/Dockerfile -t $API_URL:latest .
docker build -f email-worker/Dockerfile -t $EMAIL_URL:latest .
docker build -f whatsapp-worker/Dockerfile -t $WA_URL:latest .

docker push $API_URL:latest
docker push $EMAIL_URL:latest
docker push $WA_URL:latest
```

**Force ECS service deployment** (to pick up new images):
```bash
aws ecs update-service \
  --cluster cg-notification-cluster \
  --service notification-api-service \
  --force-new-deployment \
  --region $AWS_REGION

aws ecs update-service \
  --cluster cg-notification-cluster \
  --service email-worker-service \
  --force-new-deployment \
  --region $AWS_REGION

aws ecs update-service \
  --cluster cg-notification-cluster \
  --service whatsapp-worker-service \
  --force-new-deployment \
  --region $AWS_REGION
```

### Step 9: Verify Deployment Success

**Check ECS services are running:**
```bash
aws ecs describe-services \
  --cluster cg-notification-cluster \
  --services notification-api-service email-worker-service whatsapp-worker-service \
  --query "services[*].[serviceName,runningCount,desiredCount,status]" \
  --output table \
  --region $AWS_REGION
```

**Expected output:**
```
------------------------------------------------------------------
|                    DescribeServices                    |
+--------------------------+--------------+-------------+--------+
|  notification-api-service |  2           |  2          |  ACTIVE |
|  email-worker-service     |  2           |  2          |  ACTIVE |
|  whatsapp-worker-service  |  2           |  2          |  ACTIVE |
+--------------------------+--------------+-------------+--------+
```

**Get ALB DNS name:**
```bash
terraform output alb_dns_name
```

**Test health endpoint:**
```bash
curl http://$(terraform output -raw alb_dns_name)/actuator/health
```

**Expected output:**
```json
{"status":"UP"}
```

**Check RDS status:**
```bash
aws rds describe-db-instances \
  --db-instance-identifier cg-notification-db \
  --query "DBInstances[0].[DBInstanceStatus,Endpoint.Address,MultiAZ]" \
  --output table \
  --region $AWS_REGION
```

**Expected output:**
```
----------------------------------------
|      DescribeDBInstances      |
+----------------+---------------------+----------+
|  available     |  cg-notification-...|  True    |
+----------------+---------------------+----------+
```

**Check MSK cluster status:**
```bash
aws kafka list-clusters \
  --query "ClusterInfoList[?ClusterName=='cg-notification-msk'].[ClusterName,State]" \
  --output table \
  --region $AWS_REGION
```

**Expected output:**
```
---------------------------
|   ListClusters    |
+-------------------+--------+
|  cg-notification-msk |  ACTIVE |
+-------------------+--------+
```

**Check CloudWatch logs:**
```bash
# API service logs
aws logs tail /ecs/cg-notification-api --follow --region $AWS_REGION

# Email worker logs
aws logs tail /ecs/cg-notification-email-worker --follow --region $AWS_REGION

# WhatsApp worker logs
aws logs tail /ecs/cg-notification-whatsapp-worker --follow --region $AWS_REGION
```

---

## Quick Deployment Script

Save this as `deploy.sh` in the **terraform** directory:

```bash
#!/bin/bash
set -e

export AWS_PROFILE=${AWS_PROFILE:-sourav-admin}
export AWS_REGION=${AWS_REGION:-ap-south-1}

cd "$(dirname "$0")"   # terraform dir

echo "Step 1: Verifying AWS identity..."
aws sts get-caller-identity

echo "Step 2: Initializing Terraform..."
terraform init

echo "Step 3: Validating configuration..."
terraform validate

echo "Step 4: Generating plan..."
terraform plan -out=tfplan

echo "Step 5: Applying infrastructure (this takes 15-30 minutes)..."
terraform apply tfplan

echo "Step 6: Injecting secrets (set WASENDER_API_KEY, SENDGRID_API_KEY, SENDGRID_FROM_EMAIL, SENDGRID_FROM_NAME, ADMIN_API_KEY first)..."
./inject-secrets.sh

echo "✅ Apply complete. Next: inject secrets, run migrations, create MSK topics, build/push images, force ECS deploy."
echo "  See DEPLOYMENT.md Steps 6.5, 6.6, 7, 8, 9."
```

**Make it executable:**
```bash
chmod +x deploy.sh
```

**Run it:**
```bash
./deploy.sh
```

---

## Safe Destruction (Optional)

**⚠️ WARNING**: This will destroy ALL infrastructure. Use with extreme caution.

**Before destroying:**
1. Backup any important data from RDS
2. Export any important logs from CloudWatch
3. Note down any manual configurations

**Generate destroy plan first:**
```bash
terraform plan -destroy -out=destroy-plan
```

**Review the plan:**
```bash
terraform show destroy-plan
```

**Apply destruction (if sure):**
```bash
terraform apply destroy-plan
```

**Notes:**
- RDS has `deletion_protection = true` - disable it first if needed:
  ```bash
  aws rds modify-db-instance \
    --db-instance-identifier cg-notification-db \
    --no-deletion-protection \
    --apply-immediately \
    --region $AWS_REGION
  ```
- Final snapshot will be created automatically (if `skip_final_snapshot = false`)
- Secrets in Secrets Manager will remain (not managed by Terraform)
- S3 bucket will be deleted (ensure no important data)

---

## Common Failure Modes and Exact Fixes

| Symptom | Fix |
|--------|-----|
| `No valid credential sources` | Export `AWS_PROFILE` and `AWS_REGION`; run `aws sts get-caller-identity`. |
| ECS tasks `STOPPED`, `ResourceNotFoundException` / `AccessDenied` for secrets | Inject secrets (Step 7). Verify with `aws secretsmanager list-secrets --query 'SecretList[?contains(Name,\`cg-notification\`)].Name' --output table --region $AWS_REGION`. |
| `CannotPullContainerError` | Build and push images (Step 8); force ECS deployment. |
| ALB targets unhealthy, 503 | Wait for health-check grace (5 min). Check `/actuator/health/liveness` and CloudWatch logs. |
| Kafka `TopicAuthorizationException` or `UNKNOWN_TOPIC_OR_PARTITION` | Create MSK topics: `./scripts/create-msk-topics.sh`. Ensure IAM has MSK actions on cluster/topic/group ARNs. |
| RDS connection timeout | Confirm RDS Proxy `available`; check security groups (5432 from ECS). Wait 5–10 min after RDS create. |
| Migrations fail (`psql` not found, etc.) | Use ECS migrations: `./run-migrations-ecs.sh`. Do not rely on `null_resource` for production. |
| `create-msk-topics.sh`: "error storing credentials" on docker login | Typical with ECR credential helper. Script ignores login failure; `docker push` still works. |
| `create-msk-topics.sh`: task fails, "Class ... IAMClientCallbackHandler not found" | Rebuild kafka-admin image (Dockerfile installs aws-msk-iam-auth into Kafka libs). Re-run script. |

See [Troubleshooting](#troubleshooting) below for detailed steps and commands.

---

## Troubleshooting

### Terraform apply fails with "secret not found"

**Cause**: Secret values not injected yet (this is expected on first apply)

**Fix**:
- First apply creates secret containers
- Inject secret values using `./inject-secrets.sh` or manual commands
- ECS tasks will start successfully after secrets are injected

### ECS tasks failing to start

**Symptoms:**
- Tasks show as `STOPPED` in ECS console
- `runningCount` is less than `desiredCount`

**Check task stop reasons:**
```bash
aws ecs describe-tasks \
  --cluster cg-notification-cluster \
  --tasks <TASK_ARN> \
  --query "tasks[0].stoppedReason" \
  --region $AWS_REGION
```

**Common issues:**

1. **Secrets Manager access denied**
   - **Error**: `ResourceNotFoundException` or `AccessDeniedException`
   - **Fix**: Verify secrets are injected and IAM role has permissions
   - **Check**: `aws iam get-role-policy --role-name cg-notification-ecs-task-execution-role --policy-name SecretsManagerAccess`

2. **ECR image pull failed**
   - **Error**: `CannotPullContainerError`
   - **Fix**: Build and push Docker images (see Step 8)
   - **Check**: `aws ecr describe-images --repository-name cg-notification/api --region $AWS_REGION`

3. **Health check failing**
   - **Error**: Tasks start but immediately stop
   - **Fix**: Check CloudWatch logs for application errors
   - **Check**: `aws logs tail /ecs/cg-notification-api --follow --region $AWS_REGION`

4. **Database connection issues**
   - **Error**: `Connection refused` or `Timeout`
   - **Fix**:
     - Verify RDS Proxy is active: `aws rds describe-db-proxies --db-proxy-name cg-notification-db-proxy --region $AWS_REGION`
     - Check security groups allow traffic
     - Verify secrets are correct

### RDS connection issues

**Symptoms:**
- Application cannot connect to database
- Connection timeouts

**Verify RDS status:**
```bash
aws rds describe-db-instances \
  --db-instance-identifier cg-notification-db \
  --query "DBInstances[0].[DBInstanceStatus,Endpoint.Address]" \
  --output table \
  --region $AWS_REGION
```

**Verify RDS Proxy:**
```bash
aws rds describe-db-proxies \
  --db-proxy-name cg-notification-db-proxy \
  --query "DBProxies[0].[Status,Endpoint]" \
  --output table \
  --region $AWS_REGION
```

**Check security groups:**
- RDS security group should allow port 5432 from ECS security group
- RDS Proxy security group should allow port 5432 from ECS security group

**Fix:**
- Wait 5-10 minutes after RDS creation (RDS Proxy takes time to become ready)
- Verify security group rules are correct
- Check CloudWatch logs for connection errors

### MSK connection issues

**Symptoms:**
- Kafka producers/consumers cannot connect
- `Connection refused` errors

**Verify MSK cluster:**
```bash
aws kafka list-clusters \
  --query "ClusterInfoList[?ClusterName=='cg-notification-msk'].[ClusterName,State]" \
  --output table \
  --region $AWS_REGION
```

**Check security groups:**
- MSK security group should allow port 9098 (SASL/IAM) from ECS security group
- MSK security group should allow ephemeral ports (1024-65535) from ECS security group

**Check IAM permissions:**
```bash
aws iam get-role-policy \
  --role-name cg-api-task-role \
  --policy-name MSK-S3-Access \
  --region $AWS_REGION
```

**Fix:**
- Verify MSK cluster is `ACTIVE` (takes 5-10 minutes to create)
- Check IAM permissions for MSK access
- Verify security group rules allow traffic

### ALB health check failing

**Symptoms:**
- ALB shows targets as unhealthy
- HTTP 503 errors

**Check target health:**
```bash
aws elbv2 describe-target-health \
  --target-group-arn $(aws elbv2 describe-target-groups \
    --names cg-notification-api-tg \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text \
    --region $AWS_REGION) \
  --region $AWS_REGION
```

**Common issues:**
1. **Application not ready**: Wait for ECS tasks to fully start (health check grace period is 5 minutes)
2. **Wrong health check path**: Verify path is `/actuator/health/liveness`
3. **Port mismatch**: Verify container port is 8080

**Fix:**
- Check ECS task logs for application errors
- Verify health check path is correct
- Wait for tasks to become healthy (may take 5-10 minutes)

### Database migrations failed

**Symptoms:**
- `null_resource.db_migrations` fails during apply
- Error: `psql: command not found`
- Error: `ResourceNotFoundException` when accessing RDS Proxy

**Cause**:
- PostgreSQL client (`psql`) not installed locally (for null_resource)
- Or RDS Proxy not ready yet

**Fix for null_resource migrations:**
- Install PostgreSQL client:
  - Ubuntu/Debian: `sudo apt-get install postgresql-client`
  - macOS: `brew install postgresql`
  - RHEL/CentOS: `sudo yum install postgresql`
- Wait for RDS Proxy to be ready (can take 5-10 minutes after RDS creation)

**Recommended Fix: Use ECS Task Migrations Instead**
```bash
cd terraform
./run-migrations-ecs.sh
```

This avoids local dependencies and works in CI/CD pipelines.

### Terraform state locked

**Symptoms:**
- Error: `Error acquiring the state lock`

**Fix:**
- Check if another Terraform process is running
- If process is stuck, force unlock (use with caution):
  ```bash
  terraform force-unlock <LOCK_ID>
  ```

### Insufficient permissions

**Symptoms:**
- Error: `AccessDenied` or `UnauthorizedOperation`

**Fix:**
- Verify IAM user/role has required permissions
- Check `AWS_PROFILE` is set correctly
- Verify credentials are not expired

**Required permissions:**
- Full access to: VPC, ECS, ECR, RDS, MSK, ALB, S3, IAM, Secrets Manager, CloudWatch, KMS, WAF

---

## Important Notes

1. **AWS Profile**: **ALWAYS** export `AWS_PROFILE` and `AWS_REGION` before running any commands.
2. **Region**: All resources deploy to `ap-south-1` (default in variables; override via `AWS_REGION` / tfvars).
3. **State**: Terraform state is local (`terraform.tfstate`). **Secure it**: it contains the RDS password (from `random_password` and Secrets Manager secret version). Use remote backend (e.g. S3 + DynamoDB) for production.
4. **Secrets**: App secrets (SendGrid, WASender, Admin API key) are **not** in Terraform; inject via script or AWS CLI. DB password **is** generated and stored by Terraform (see above).
5. **RDS Password**: Auto-generated by Terraform. Do **not** manually change it in Secrets Manager—RDS will not auto-sync.
6. **MSK Topics**: Create `notifications-email` and `notifications-whatsapp` via `./scripts/create-msk-topics.sh` before sending messages.
7. **ALB Access Logs**: Enabled, S3, 90-day retention. **KMS**: Customer-managed keys for RDS and S3.
8. **Image tag**: Use `var.image_tag` (default `latest`). For rollback-safe deploys, set `IMAGE_TAG=$(git rev-parse --short HEAD)` and `terraform apply -var="image_tag=$IMAGE_TAG"` (or use `deploy-trigger.sh` / `deploy-to-aws.sh` with `IMAGE_TAG` set). Task definitions use this tag; redeploy by changing it and applying.
9. **Stateful infra**: RDS, Redis, MSK, and S3 uploads bucket have `lifecycle { prevent_destroy = true }`. Terraform will not destroy them. To replace (e.g. Redis auth migration), temporarily remove the lifecycle block, apply, then restore it.
10. **Redis AUTH**: ElastiCache uses `auth_token` (TLS + AUTH). API receives `SPRING_DATA_REDIS_PASSWORD` from Secrets Manager. Terraform manages the token.

---

## Post-Deployment Checklist

- [ ] `aws sts get-caller-identity` matches intended account
- [ ] All ECS services running (`runningCount == desiredCount`)
- [ ] ALB health checks passing; `curl https://<alb-dns>/actuator/health` → `{"status":"UP"}`
- [ ] RDS `available`, Multi-AZ; RDS Proxy `available`
- [ ] MSK cluster `ACTIVE`; MSK topics created (`./scripts/create-msk-topics.sh`)
- [ ] All app secrets injected; Docker images pushed to ECR
- [ ] CloudWatch logs flowing; ALB access logs in S3; WAF associated with ALB
- [ ] Database migrations completed (`./run-migrations-ecs.sh`)

---

## Support

For issues or questions:
1. Check CloudWatch logs for application errors
2. Review Terraform plan output for resource issues
3. Verify AWS service status: https://status.aws.amazon.com/
4. Check this guide's troubleshooting section
