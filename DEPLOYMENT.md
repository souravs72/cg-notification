# Deployment Guide

Complete guide for deploying the Notification System in various environments.

## Table of Contents

- [Quick Start](#quick-start)
- [Docker Deployment](#docker-deployment)
- [Local Development](#local-development)
- [Production Deployment](#production-deployment)
- [Environment Variables](#environment-variables)
- [Troubleshooting](#troubleshooting)

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Maven 3.9+ (for local development)
- Java 17+ (for local development)
- SendGrid API Key
- WASender API Key

### Using Start Script (Recommended)

```bash
# Make script executable
chmod +x start.sh

# Run the script
./start.sh
```

The script will:
- Check prerequisites
- Build common-proto module
- Start infrastructure (PostgreSQL, Kafka, Zookeeper)
- Build and start all application services
- Display service URLs

## Docker Deployment

### Environment Setup

Create a `.env` file in the root directory:

```bash
WASENDER_API_KEY=your-wasender-api-key-here
SENDGRID_API_KEY=your-sendgrid-api-key
SENDGRID_FROM_EMAIL=noreply@yourdomain.com
SENDGRID_FROM_NAME=Your Company Name
```

### Start All Services

```bash
# Start everything
docker compose up -d

# View logs
docker compose logs -f

# Check status
docker compose ps

# Stop services
docker compose down
```

### Service URLs

- **Notification API**: http://localhost:8080
- **Email Worker**: http://localhost:8081/actuator/health
- **WhatsApp Worker**: http://localhost:8083/actuator/health
- **Kafka UI**: http://localhost:8089
- **Admin Dashboard**: http://localhost:8080/admin/dashboard

### Rebuilding After Code Changes

```bash
# Rebuild specific service
docker compose build notification-api
docker compose up -d notification-api

# Rebuild all services
docker compose build --no-cache
docker compose up -d
```

### Data Persistence

- PostgreSQL data is persisted in Docker volume `postgres_data`
- To remove all data: `docker compose down -v`

## Local Development

### Step 1: Start Infrastructure

```bash
docker compose up -d postgres zookeeper kafka
```

Wait for services to be healthy (~30 seconds):
```bash
docker compose ps
```

### Step 2: Build Common Proto

```bash
cd common-proto
mvn clean install -DskipTests
cd ..
```

### Step 3: Set Environment Variables

```bash
export WASENDER_API_KEY=your-wasender-api-key
export SENDGRID_API_KEY=your-sendgrid-api-key
export SENDGRID_FROM_EMAIL=noreply@yourdomain.com
export SENDGRID_FROM_NAME=Your Company Name
```

### Step 4: Start Services

**Terminal 1 - Notification API:**
```bash
cd notification-api
mvn spring-boot:run
```

**Terminal 2 - Email Worker:**
```bash
cd email-worker
mvn spring-boot:run
```

**Terminal 3 - WhatsApp Worker:**
```bash
cd whatsapp-worker
mvn spring-boot:run
```

### Step 5: Verify Deployment

```bash
# Check API health
curl http://localhost:8080/actuator/health

# Register a site
curl -X POST http://localhost:8080/api/v1/site/register \
  -H "Content-Type: application/json" \
  -d '{"siteName": "test-site"}'

# Send test notification (use API key from registration)
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: YOUR_API_KEY" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "+1234567890",
    "body": "Test message"
  }'
```

## Deploy on AWS

Use the existing Terraform + scripts to deploy to AWS (ECS Fargate, RDS, Redis, ALB, SNS/SQS).

### Prerequisites

- **AWS CLI** – `aws --version`, credentials via `aws configure` or `AWS_PROFILE`
- **Terraform** ≥ 1.0 – `terraform version`
- **Docker** – for building and pushing images
- **jq**, **curl** – for deploy script health checks

### 1. Set AWS identity

```bash
export AWS_PROFILE=your-profile    # or use aws configure
export AWS_REGION=ap-south-1       # or your region
aws sts get-caller-identity        # verify
```

### 2. One-time Terraform config

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars: set s3_bucket_name (globally unique), region, counts, etc.
# Optional: configure backend in backend.tf for remote state (see backend.tf.example)
cd ..
```

### 3. Set secrets (for first deploy or secret rotation)

Export these **before** running the deploy script (they are written to AWS Secrets Manager):

```bash
export WASENDER_API_KEY=your-wasender-api-key
export SENDGRID_API_KEY=your-sendgrid-api-key
export SENDGRID_FROM_EMAIL=noreply@yourdomain.com
export SENDGRID_FROM_NAME="Your App"
export ADMIN_API_KEY=your-secure-admin-api-key
```

### 4. Full deployment (infra + app)

From the **project root**:

```bash
chmod +x scripts/deploy-trigger.sh
./scripts/deploy-trigger.sh
```

This will: Terraform apply (VPC, RDS, Redis, ECS, ALB, ECR, SNS/SQS, WAF, etc.) → inject secrets → run DB migrations → build and push Docker images → deploy ECS services → wait for stability → run health checks.

**Optional – custom domain and HTTPS:**

```bash
./scripts/deploy-trigger.sh --domain=notifications.yourdomain.com --route53-zone-id=Z1234567890ABC
```

If you omit `--route53-zone-id`, the script prints the ACM CNAME records for you to add in your DNS provider.

**Subsequent deploys (app only, no infra changes):**

```bash
./scripts/deploy-to-aws.sh
# Or with a specific image tag:
./scripts/deploy-to-aws.sh --image-tag=$(git rev-parse --short HEAD)
```

Or skip Terraform but still run migrations and build/push:

```bash
./scripts/deploy-trigger.sh --skip-terraform
```

### 5. After deploy

- **API URL:** `http://<alb_dns_name>` (from `terraform output alb_dns_name`) or your custom domain if set.
- **Health:** `curl http://<alb_dns>/actuator/health`
- **Admin:** `http://<alb_dns>/admin/dashboard` (use `X-Admin-Key` or log in via `/auth/login`)
- **Logs:** `aws logs tail /ecs/cg-notification-api --follow --region $AWS_REGION`

Full details, troubleshooting, and manual steps: `terraform/DEPLOYMENT.md` and `scripts/deploy-trigger.sh --help`.

---

## Production Deployment

### Security Checklist

- [ ] Change default database passwords
- [ ] Use Docker secrets or external secret management
- [ ] Enable HTTPS/TLS
- [ ] Configure firewall rules
- [ ] Set up monitoring and alerting
- [ ] Configure log aggregation
- [ ] Set up backup strategy
- [ ] Enable authentication for admin dashboard

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://your-db-host:5432/notification_db
SPRING_DATASOURCE_USERNAME=your-db-user
SPRING_DATASOURCE_PASSWORD=your-secure-password

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=your-kafka-host:9092

# API Keys
SENDGRID_API_KEY=your-production-sendgrid-key
WASENDER_API_KEY=your-production-wasender-key
SENDGRID_FROM_EMAIL=noreply@yourdomain.com
SENDGRID_FROM_NAME=Your Company
```

### Docker Compose Override

Create `docker-compose.override.yml` for production:

```yaml
services:
  notification-api:
    environment:
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME}
      SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}
    restart: unless-stopped

  email-worker:
    environment:
      SENDGRID_API_KEY: ${SENDGRID_API_KEY}
    restart: unless-stopped

  whatsapp-worker:
    environment:
      WASENDER_API_KEY: ${WASENDER_API_KEY}
    restart: unless-stopped
```

## Environment Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `WASENDER_API_KEY` | WASender API key for WhatsApp | `your-wasender-api-key` |
| `SENDGRID_API_KEY` | SendGrid API key for emails | `SG.xxxxx` |
| `SENDGRID_FROM_EMAIL` | Default sender email | `noreply@yourdomain.com` |
| `SENDGRID_FROM_NAME` | Default sender name | `Your Company` |

### Optional Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | Database URL | `jdbc:postgresql://postgres:5432/notification_db` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `notification_user` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `notification_pass` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `kafka:29092` |

## Troubleshooting

### Services Won't Start

1. **Check ports are available:**
```bash
netstat -tulpn | grep -E '8080|8081|8083|5433|9092'
```

2. **Check Docker logs:**
```bash
docker compose logs [service-name]
```

3. **Verify Docker is running:**
```bash
docker info
```

### Database Connection Issues

```bash
# Connect to database
docker exec -it notification-postgres psql -U notification_user -d notification_db

# Check tables
\dt

# Check message logs
SELECT COUNT(*) FROM message_logs;

# Check sites
SELECT site_name, is_active FROM frappe_sites;
```

### Kafka Issues

```bash
# List topics
docker exec -it notification-kafka kafka-topics --list --bootstrap-server localhost:9092

# Check consumer groups
docker exec -it notification-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list

# View Kafka UI
open http://localhost:8089
```

### Worker Not Processing Messages

1. Check worker logs:
```bash
docker compose logs email-worker
docker compose logs whatsapp-worker
```

2. Verify API keys are set:
```bash
docker compose config | grep -E "SENDGRID|WASENDER"
```

3. Check message logs for errors:
```bash
docker exec -it notification-postgres psql -U notification_user -d notification_db \
  -c "SELECT message_id, status, error_message FROM message_logs WHERE status = 'FAILED' LIMIT 10;"
```

### Build Failures

```bash
# Clean and rebuild
mvn clean install -DskipTests

# Rebuild Docker images
docker compose build --no-cache

# Check Maven version
mvn -version  # Should be 3.9+
```

### Health Check Failures

```bash
# Check API health
curl http://localhost:8080/actuator/health

# Check worker health
curl http://localhost:8081/actuator/health
curl http://localhost:8083/actuator/health

# View detailed health info
curl http://localhost:8080/actuator/health | jq
```

## Monitoring

### Health Endpoints

- API: `http://localhost:8080/actuator/health`
- Email Worker: `http://localhost:8081/actuator/health`
- WhatsApp Worker: `http://localhost:8083/actuator/health`

### Prometheus Metrics

- API: `http://localhost:8080/actuator/prometheus`
- Email Worker: `http://localhost:8081/actuator/prometheus`
- WhatsApp Worker: `http://localhost:8083/actuator/prometheus`

### Kafka UI

Access at `http://localhost:8089` to monitor:
- Topics and partitions
- Consumer groups
- Message throughput
- Consumer lag

### Admin Dashboard

Access at `http://localhost:8080/admin/dashboard` for:
- Overall metrics
- Site-wise statistics
- Recent messages
- Success rates

## Next Steps

After successful deployment:

1. Register your first site via API
2. Configure SendGrid sender verification
3. Set up WASender WhatsApp session
4. Test sending notifications
5. Set up monitoring dashboards (Grafana + Prometheus)
6. Configure production secrets management
7. Set up CI/CD pipeline

