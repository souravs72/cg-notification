# Quick Start: Local Testing Before AWS Deployment

## One-command workflow

Test locally and (optionally) deploy to AWS:

```bash
./scripts/local-test-and-deploy.sh
```

This script will:
1. Start local Docker Compose using `docker-compose.ecs-test.yml` (ECS-like config, `prod` profile).
2. Test all health and liveness probes for API, email-worker, and whatsapp-worker.
3. On success, ask whether you want to build images and deploy to AWS.
4. On failure, print why and exit without touching AWS.

## Manual local test-only flow

### 1. Start services

```bash
docker-compose -f docker-compose.ecs-test.yml up -d --build
```

### 2. Run health probe checks

```bash
./scripts/test-health-probes.sh
```

The script verifies:
- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- Database connectivity
- Kafka connectivity

### 3. Deploy to AWS (optional)

If local tests pass and you want to deploy:

```bash
./scripts/deploy-to-aws.sh
```

## Required environment

Before running any of the scripts, set:

```bash
export ADMIN_API_KEY="your-admin-key"
export WASENDER_API_KEY="your-wasender-key"
export SENDGRID_API_KEY="your-sendgrid-key"
export SENDGRID_FROM_EMAIL="your-email@example.com"
export SENDGRID_FROM_NAME="Your Service Name"
```

You should also have Docker, Docker Compose, the AWS CLI, and Terraform installed, with `aws sts get-caller-identity` working for the profile/region you intend to use.

## Troubleshooting (local)

```bash
# See logs for all services
docker-compose -f docker-compose.ecs-test.yml logs -f

# Restart a specific service
docker-compose -f docker-compose.ecs-test.yml restart notification-api

# Stop everything
docker-compose -f docker-compose.ecs-test.yml down
```

If `test-health-probes.sh` fails, read its output first; it usually points at DB or Kafka connectivity issues that need fixing before deployment.
