# Secrets Reference

All secrets used by the cg-notification system: where they live, who uses them, and how to set or rotate them.

## Overview

| Secret | Managed by | Format | Injected into |
|--------|------------|--------|----------------|
| `cg-notification/db-password` | Terraform | JSON `{"username","password"}` | RDS Proxy |
| `cg-notification/db-password-only` | Terraform | Plain string | API, email-worker, whatsapp-worker |
| `cg-notification/redis-password` | Terraform | Plain string | API (Spring Session Redis) |
| `cg-notification/sendgrid-api-key` | Manual / script | Plain | email-worker |
| `cg-notification/sendgrid-from-email` | Manual / script | Plain | email-worker |
| `cg-notification/sendgrid-from-name` | Manual / script | Plain | email-worker |
| `cg-notification/wasender-api-key` | Manual / script | Plain | API, whatsapp-worker |
| `cg-notification/admin-api-key` | Manual / script | Plain | API |

## 1. Database secrets (Terraform-managed)

**⚠️ Do not change manually** — these are generated and managed by Terraform.

- **`cg-notification/db-password`**: JSON `{"username","password"}` for RDS Proxy. Consumers: RDS Proxy only.
- **`cg-notification/db-password-only`**: Plain string for Spring Boot `SPRING_DATASOURCE_PASSWORD`. Consumers: notification-api, email-worker, whatsapp-worker.
- **`cg-notification/redis-password`**: Plain string for ElastiCache Redis AUTH (`SPRING_DATA_REDIS_PASSWORD`). Consumers: notification-api only.

**Rotation**: For DB secrets, update Terraform and apply (do not change in Secrets Manager alone — RDS won't sync). For Redis, update both the replication group auth token and the secret.

## 2. Application secrets (manual / script)

These are **not** stored in Terraform. Set values **after** the first `terraform apply` using `terraform/inject-secrets.sh` (recommended) or AWS CLI.

| Secret | Consumers | Env var |
|--------|-----------|---------|
| `cg-notification/sendgrid-api-key` | email-worker | `SENDGRID_API_KEY` |
| `cg-notification/sendgrid-from-email` | email-worker | `SENDGRID_FROM_EMAIL` |
| `cg-notification/sendgrid-from-name` | email-worker | `SENDGRID_FROM_NAME` |
| `cg-notification/wasender-api-key` | notification-api, whatsapp-worker | `WASENDER_API_KEY` |
| `cg-notification/admin-api-key` | notification-api | `ADMIN_API_KEY` |

## 3. Setting application secrets

**Recommended**: Use `terraform/inject-secrets.sh`:

```bash
export AWS_PROFILE=your-profile
export AWS_REGION=ap-south-1
export WASENDER_API_KEY="your-key"
export SENDGRID_API_KEY="your-key"
export SENDGRID_FROM_EMAIL="noreply@example.com"
export SENDGRID_FROM_NAME="Notification Service"
export ADMIN_API_KEY="your-key"

./terraform/inject-secrets.sh
```

**Alternative**: Use AWS CLI directly (see `terraform/DEPLOYMENT.md` Step 7 for commands).

**Verify**: `aws secretsmanager list-secrets --query 'SecretList[?contains(Name, `cg-notification`)].Name' --output table --region $AWS_REGION`

## 4. Where secrets are used (ECS)

| Task | Secrets (env vars) |
|------|--------------------|
| notification-api | `SPRING_DATASOURCE_PASSWORD`, `SPRING_DATA_REDIS_PASSWORD`, `ADMIN_API_KEY`, `WASENDER_API_KEY` |
| email-worker | `SPRING_DATASOURCE_PASSWORD`, `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, `SENDGRID_FROM_NAME` |
| whatsapp-worker | `SPRING_DATASOURCE_PASSWORD`, `WASENDER_API_KEY` |

ECS tasks resolve these via `secrets[].valueFrom` (Secrets Manager ARNs). The task execution role must have `secretsmanager:GetSecretValue` permissions; see `terraform/iam.tf`.

## 5. Security & rotation

- **No secrets in code or Terraform**: Application secrets are not in repo or Terraform config.
- **Encryption**: All secrets encrypted at rest in AWS Secrets Manager.
- **Rotation**: For application secrets, update values in Secrets Manager and force a new ECS deployment. Do not rotate DB secrets without coordinating RDS + Terraform changes.

## Related

- `terraform/secrets.tf` — Terraform secret definitions
- `terraform/inject-secrets.sh` — Injection script
- `terraform/DEPLOYMENT.md` — Deployment flow
