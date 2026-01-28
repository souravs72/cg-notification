# Notification System

A production-grade, multi-tenant notification platform built with Spring Boot, Kafka, and gRPC, supporting WhatsApp (via WASender) and Email (via SendGrid) delivery.

## 📋 TL;DR for Developers

**What is this?** A microservices-based notification system that sends emails and WhatsApp messages via Kafka message queues.

**Quick start:**
1. Install Docker & Docker Compose
2. Create `.env` with `WASENDER_API_KEY` and `SENDGRID_API_KEY`
3. Run `docker compose up -d`
4. Access API at http://localhost:8080

**Key concepts:**
- **Multi-tenant**: Each site has isolated data via `site_id`
- **Kafka**: Async message processing between API and workers
- **Workers**: Separate services for email (SendGrid) and WhatsApp (WASender)
- **gRPC**: Inter-service communication for metrics and status updates

**Architecture**: `notification-api` (REST gateway) → Kafka → `email-worker` / `whatsapp-worker` → External APIs

---

## 📑 Table of Contents

- [Notification System](#notification-system)
  - [📋 TL;DR for Developers](#-tldr-for-developers)
  - [📑 Table of Contents](#-table-of-contents)
  - [🚀 Quick Start](#-quick-start)
    - [Prerequisites](#prerequisites)
    - [Environment Setup](#environment-setup)
    - [Running with Docker Compose (Recommended)](#running-with-docker-compose-recommended)
    - [Running Locally (Development)](#running-locally-development)
  - [📡 API Documentation](#-api-documentation)
    - [Register Site](#register-site)
    - [Send Email Notification](#send-email-notification)
    - [Send WhatsApp Notification](#send-whatsapp-notification)
  - [🛠️ Development](#️-development)
    - [Building](#building)
    - [Testing](#testing)
      - [Unit Tests](#unit-tests)
      - [Integration Tests](#integration-tests)
    - [Code Style](#code-style)
    - [Project Structure](#project-structure)
  - [🚢 Deployment](#-deployment)
    - [Local vs AWS](#local-vs-aws)
    - [Local profile override (optional)](#local-profile-override-optional)
    - [Rebuilding After Code Changes](#rebuilding-after-code-changes)
  - [⚙️ Configuration](#️-configuration)
    - [Environment Variables Reference](#environment-variables-reference)
    - [WhatsApp Session Configuration](#whatsapp-session-configuration)
  - [🔧 Troubleshooting](#-troubleshooting)
    - [Services Won't Start](#services-wont-start)
    - [Database Connection Issues](#database-connection-issues)
    - [Kafka Issues](#kafka-issues)
    - [Worker Not Processing Messages](#worker-not-processing-messages)
    - [WASender API Key Issues](#wasender-api-key-issues)
    - [Local Development Issues](#local-development-issues)
    - [Build Failures](#build-failures)
    - [Health Check Failures](#health-check-failures)
  - [📊 Monitoring](#-monitoring)
    - [Health Endpoints](#health-endpoints)
    - [Prometheus Metrics](#prometheus-metrics)
    - [Kafka UI](#kafka-ui)
    - [Admin Dashboard](#admin-dashboard)
  - [📚 Documentation](#-documentation)
  - [📚 Additional Resources](#-additional-resources)
  - [📄 License](#-license)
  - [👥 Support](#-support)

---

## 🚀 Quick Start

### Prerequisites

- **Docker & Docker Compose** (for infrastructure)
- **Maven 3.9+** (for local development)
- **Java 17+** (for local development)
- **SendGrid API Key** - Get from [SendGrid](https://sendgrid.com)
- **WASender API Key** - Get from [WASender](https://wasenderapi.com)

### Environment Setup

Create a `.env` file in the root directory:

```bash
WASENDER_API_KEY=your-wasender-api-key-here
SENDGRID_API_KEY=your-sendgrid-api-key
SENDGRID_FROM_EMAIL=noreply@yourdomain.com
SENDGRID_FROM_NAME=Your Company Name
```

### Running with Docker Compose (Recommended)

```bash
# Start all services
docker compose up -d

# View logs
docker compose logs -f

# Check status
docker compose ps

# Stop services
docker compose down
```

**Service URLs:**

- **Notification API**: http://localhost:8080
- **API Documentation (Swagger UI)**: http://localhost:8080/swagger-ui.html
- **Admin Dashboard**: http://localhost:8080/admin/dashboard
- **Email Worker Health**: http://localhost:8081/actuator/health
- **WhatsApp Worker Health**: http://localhost:8083/actuator/health
- **Kafka UI**: http://localhost:8089

### Running Locally (Development)

1. **Start Infrastructure:**

```bash
docker compose up -d postgres zookeeper kafka
```

Wait ~30 seconds for services to be healthy, then verify:

```bash
docker compose ps
```

**Connection Details:**

- PostgreSQL: `localhost:5433` (username: `notification_user`, password: `notification_pass`, database: `notification_db`)
- Kafka: `localhost:9092`

2. **Build Common Proto:**

```bash
cd common-proto && mvn clean install && cd ..
```

Or build everything:

```bash
mvn clean install -DskipTests
```

3. **Set Environment Variables:**

```bash
export WASENDER_API_KEY=your-wasender-api-key
export SENDGRID_API_KEY=your-sendgrid-api-key
export SENDGRID_FROM_EMAIL=noreply@yourdomain.com
export SENDGRID_FROM_NAME=Your Company Name
```

4. **Run Services** (each in a separate terminal):

**Terminal 1 - Notification API:**

```bash
cd notification-api
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 2 - Email Worker:**

```bash
cd email-worker
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Terminal 3 - WhatsApp Worker:**

```bash
cd whatsapp-worker
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

5. **Verify Setup:**

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

## 📡 API Documentation

**OpenAPI/Swagger UI:**

- **Notification API**: http://localhost:8080/swagger-ui.html
- **Email Worker**: http://localhost:8081/swagger-ui.html
- **WhatsApp Worker**: http://localhost:8083/swagger-ui.html

**API Docs (JSON):**

- http://localhost:8080/api-docs
- http://localhost:8081/api-docs
- http://localhost:8083/api-docs

See [API.md](API.md) for additional API reference.

### Register Site

```bash
curl -X POST http://localhost:8080/api/v1/site/register \
  -H "Content-Type: application/json" \
  -d '{"siteName": "my-site", "description": "Production site"}'
```

**⚠️ Important:** Save the `apiKey` from the response - it's only shown once!

### Send Email Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Welcome!",
    "body": "Welcome to our service",
    "isHtml": true
  }'
```

### Send WhatsApp Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "+1234567890",
    "body": "Hello from WhatsApp!"
  }'
```

## 🛠️ Development

### Building

```bash
# Build all modules
mvn clean install

# Build specific module
cd notification-api && mvn clean package
```

### Testing

#### Unit Tests

```bash
# Run all unit tests (no database required)
mvn test

# Run specific module tests
cd notification-api && mvn test
```

#### Integration Tests

Integration tests require Docker and Testcontainers (PostgreSQL and Kafka containers).

**Default Behavior:**
- Integration tests are **skipped by default** (`skipITs=true`) for local development
- This allows `mvn clean verify` to run without Docker

**Running integration tests (Docker required):**

```bash
docker compose -f docker-compose.test.yml up -d
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/notification_db"
export SPRING_DATASOURCE_USERNAME="notification_user"
export SPRING_DATASOURCE_PASSWORD="notification_pass"
export SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
mvn clean verify -DskipITs=false
docker compose -f docker-compose.test.yml down
```

- `docker-compose.test.yml` provides Postgres, Zookeeper, Kafka for tests.
- Regular `docker-compose.yml` uses port 5433 to avoid conflicts.
- If Docker is unavailable, integration tests are skipped (default).

### Code Style

- Java 17+ features
- Spring Boot 3.x conventions
- Lombok for boilerplate reduction
- MapStruct for DTO mapping (where applicable)

### Project Structure

```
cg-notification/
├── notification-api/      # REST API gateway
├── email-worker/          # Email processing worker
├── whatsapp-worker/       # WhatsApp processing worker
├── common-proto/          # Shared gRPC protocol definitions
└── deployment/            # Database migrations and SQL scripts
```

## 🚢 Deployment

### Local vs AWS

- **Local Docker (quick dev loop)**  
  Use `docker compose up -d` with `docker-compose.yml` as shown in the quick start above.

- **Local ECS-like + deploy to AWS**  
  Use the ECS‑style compose file and helper scripts described in `README_LOCAL_TESTING.md`:
  - `docker-compose -f docker-compose.ecs-test.yml up -d --build`
  - `./scripts/test-health-probes.sh`
  - `./scripts/deploy-to-aws.sh` (optional, when you’re happy with local tests)

- **Real AWS infrastructure (ALB → ECS → RDS + MSK, WAF, etc.)**  
  Full production-ready deployment is managed via Terraform. Start here:
  - `terraform/README.md` – what the infra looks like.
  - `terraform/DEPLOYMENT.md` – exact commands and verification steps.

### Local profile override (optional)

For local development, if you prefer to run the services with the `local` Spring profile while still using `docker-compose.yml`, you can copy the example override:

```bash
cp docker-compose.override.yml.example docker-compose.override.yml
```

This sets `SPRING_PROFILES_ACTIVE=local` for the three services when you run `docker compose up`. It is only intended for local development, not for production.

### Rebuilding After Code Changes

```bash
# Rebuild specific service
docker compose build notification-api
docker compose up -d notification-api

# Rebuild all services
docker compose build --no-cache
docker compose up -d
```

## ⚙️ Configuration & Operations

For deeper details on configuration, invariants, and runtime operations (metrics, failure modes, etc.), see:

- `docs/02-invariants-and-rules.md`
- `docs/03-operations.md`
- `docs/05-security-and-logging.md`

Those docs cover what must never change, how metrics are interpreted, and where to look when things go wrong.

## 📚 Documentation

- **OpenAPI/Swagger UI**: http://localhost:8080/swagger-ui.html
- **[docs/](docs/)** - Architecture, invariants, operations, extensibility, security and logging
- **[API.md](API.md)** - Complete API reference with examples

## 📚 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [SendGrid API](https://docs.sendgrid.com/)
- [WASender API](https://wasenderapi.com/docs)

## 📄 License

This project is proprietary software.

## 👥 Support

For issues and questions, please contact the development team.
