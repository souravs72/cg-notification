#!/bin/bash
# Inject secrets into AWS Secrets Manager after first terraform apply.
# Run from terraform/ or project root. Uses AWS_PROFILE and AWS_REGION.
#
# ⚠️ EDIT the placeholder values below before running. Never commit real secrets.

set -e

REGION="${AWS_REGION:-ap-south-1}"

echo "Verifying AWS identity..."
if ! aws sts get-caller-identity > /dev/null 2>&1; then
  echo "❌ ERROR: AWS credentials not configured or invalid"
  echo "   Export AWS_PROFILE and AWS_REGION, then retry."
  exit 1
fi

echo "Using account: $(aws sts get-caller-identity --query Account --output text), region: $REGION"
echo "Injecting secrets (ensure placeholders below are replaced with real values)..."
echo ""

# Replace placeholders with your actual secret values before running.
WASENDER_API_KEY="${WASENDER_API_KEY:-YOUR-WASENDER-API-KEY}"
SENDGRID_API_KEY="${SENDGRID_API_KEY:-YOUR-SENDGRID-API-KEY}"
SENDGRID_FROM_EMAIL="${SENDGRID_FROM_EMAIL:-noreply@example.com}"
SENDGRID_FROM_NAME="${SENDGRID_FROM_NAME:-Notification Service}"
ADMIN_API_KEY="${ADMIN_API_KEY:-YOUR-ADMIN-API-KEY}"

if [ "$WASENDER_API_KEY" = "YOUR-WASENDER-API-KEY" ] || [ "$SENDGRID_API_KEY" = "YOUR-SENDGRID-API-KEY" ] || [ "$ADMIN_API_KEY" = "YOUR-ADMIN-API-KEY" ]; then
  echo "❌ ERROR: Replace placeholder secrets before running."
  echo "   Either edit this script or set env vars: WASENDER_API_KEY, SENDGRID_API_KEY, SENDGRID_FROM_EMAIL, SENDGRID_FROM_NAME, ADMIN_API_KEY"
  exit 1
fi

aws secretsmanager put-secret-value --secret-id cg-notification/wasender-api-key   --secret-string "$WASENDER_API_KEY"   --region "$REGION"
aws secretsmanager put-secret-value --secret-id cg-notification/sendgrid-api-key   --secret-string "$SENDGRID_API_KEY"   --region "$REGION"
aws secretsmanager put-secret-value --secret-id cg-notification/sendgrid-from-email --secret-string "$SENDGRID_FROM_EMAIL" --region "$REGION"
aws secretsmanager put-secret-value --secret-id cg-notification/sendgrid-from-name  --secret-string "$SENDGRID_FROM_NAME"  --region "$REGION"
aws secretsmanager put-secret-value --secret-id cg-notification/admin-api-key      --secret-string "$ADMIN_API_KEY"      --region "$REGION"

echo "✅ All secrets injected."
echo "Verify: aws secretsmanager list-secrets --query 'SecretList[?contains(Name, \`cg-notification\`)].Name' --output table --region $REGION"
