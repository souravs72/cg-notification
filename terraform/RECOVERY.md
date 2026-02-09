# Terraform State Recovery Guide

## What Happened

Terraform ran with an **empty state** (e.g. after `.terraform` was removed or backend was switched to local), so it tried to create all resources from scratch. Many resources **already exist** in AWS from a previous deployment, causing "already exists" errors.

## Current State

- **In state**: New VPC, subnets, NAT gateway, route tables, security groups, VPC endpoints (created in the failed run)
- **Not in state** (but exist in AWS): ECR repos, S3 buckets, CloudWatch log groups, IAM roles, Secrets Manager secrets, WAF, ALB target group, MSK cluster, KMS alias, RDS/Redis subnet groups

## Recovery Options

### Option A: Restore Previous State (Preferred if available)

If you have a backup of `terraform.tfstate` or use S3 backend:

1. Restore the state file to `terraform/terraform.tfstate`
2. Run `terraform init -reconfigure` (with `-backend-config` if using S3)
3. Run `terraform plan` to verify
4. Run `terraform apply` if changes are needed

### Option B: Destroy New Resources and Import Existing

If you cannot restore state:

1. **Destroy what was created** (removes the new VPC and related resources):
   ```bash
   cd terraform
   terraform destroy
   ```
   This removes: VPC, subnets, NAT, route tables, security groups, VPC endpoints. It will NOT touch ECR, S3, IAM, Secrets, etc. (they are not in state).

2. **Re-run deploy** – the deploy script will try to create everything again. The "already exists" errors will recur because those resources are still in AWS.

3. **Import existing resources** before apply. Run the import script:
   ```bash
   ./scripts/terraform-import-existing.sh
   ```
   Then run `terraform plan` and `terraform apply`.

### Option C: Use S3 Backend (Prevent Future Issues)

1. Create an S3 bucket for Terraform state (one-time):
   ```bash
   aws s3 mb s3://cg-notification-terraform-state-$(aws sts get-caller-identity --query Account --output text) --region ap-south-1
   aws s3api put-bucket-versioning --bucket cg-notification-terraform-state-$(aws sts get-caller-identity --query Account --output text) \
     --versioning-configuration Status=Enabled
   ```

2. Copy `backend.tf.example` to `backend.tf` and set the bucket name.

3. Run `terraform init -migrate-state` to move local state to S3.

## Preventing This

- **Use S3 backend** for production so state is stored remotely.
- **Do not** remove `.terraform` unless you are intentionally switching backends and understand the impact.
- Back up `terraform.tfstate` or rely on S3 versioning before major changes.
