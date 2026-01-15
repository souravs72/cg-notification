# Notification System

A production-grade, multi-tenant notification platform built with Spring Boot, Kafka, and gRPC, supporting WhatsApp (via WASender) and Email (via SendGrid) delivery.

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

```bash
# Run all tests
mvn test

# Run specific module tests
cd notification-api && mvn test
```

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

### Production Checklist

- [ ] Change default database passwords
- [ ] Use Docker secrets or external secret management (e.g., AWS Secrets Manager, HashiCorp Vault)
- [ ] Enable HTTPS/TLS
- [ ] Configure firewall rules
- [ ] Set up monitoring and alerting (Prometheus, Grafana)
- [ ] Configure log aggregation (ELK stack, CloudWatch)
- [ ] Set up backup strategy for PostgreSQL
- [ ] Enable authentication for admin dashboard
- [ ] Configure rate limiting
- [ ] Set up health check monitoring

### Production Environment Variables

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

### Rebuilding After Code Changes

```bash
# Rebuild specific service
docker compose build notification-api
docker compose up -d notification-api

# Rebuild all services
docker compose build --no-cache
docker compose up -d
```

## ⚙️ Configuration

### Environment Variables Reference

| Variable                         | Description                   | Example                                           | Required For                      |
| -------------------------------- | ----------------------------- | ------------------------------------------------- | --------------------------------- |
| `WASENDER_API_KEY`               | WASender API key for WhatsApp | `your-wasender-api-key`                           | notification-api, whatsapp-worker |
| `SENDGRID_API_KEY`               | SendGrid API key for emails   | `SG.xxxxx`                                        | email-worker                      |
| `SENDGRID_FROM_EMAIL`            | Default sender email          | `noreply@yourdomain.com`                          | email-worker                      |
| `SENDGRID_FROM_NAME`             | Default sender name           | `Your Company`                                    | email-worker                      |
| `SPRING_DATASOURCE_URL`          | Database URL                  | `jdbc:postgresql://postgres:5432/notification_db` | All services                      |
| `SPRING_DATASOURCE_USERNAME`     | Database username             | `notification_user`                               | All services                      |
| `SPRING_DATASOURCE_PASSWORD`     | Database password             | `notification_pass`                               | All services                      |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers                 | `kafka:29092`                                     | All services                      |

### WhatsApp Session Configuration

Each site can have its own WhatsApp session. To set up:

1. Navigate to Admin Dashboard → WhatsApp Sessions (http://localhost:8080/admin/sessions)
2. Create a new session with a unique name
3. Scan the QR code with WhatsApp
4. Link the session to your site

**Session Architecture Options:**

- **One Session Per Site**: Each site has its own WhatsApp account (isolated)
- **Shared Session**: All sites use the same WhatsApp account/number
- **Hybrid**: Some sites share, others have dedicated sessions

## 🔧 Troubleshooting

### Services Won't Start

```bash
# Check if ports are available
netstat -tulpn | grep -E '8080|8081|8083|5433|9092'

# Check Docker logs
docker compose logs [service-name]

# Verify Docker is running
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

1. **Check worker logs:**

```bash
docker compose logs email-worker
docker compose logs whatsapp-worker
```

2. **Verify API keys are set:**

```bash
docker compose config | grep -E "SENDGRID|WASENDER"
```

3. **Check message logs for errors:**

```bash
docker exec -it notification-postgres psql -U notification_user -d notification_db \
  -c "SELECT message_id, status, error_message FROM message_logs WHERE status = 'FAILED' LIMIT 10;"
```

4. **Verify Kafka consumer groups:**

```bash
docker exec -it notification-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

### WASender API Key Issues

**Problem**: `401 Unauthorized` from WASender API

**Solutions:**

1. Verify API key is set:

   ```bash
   echo $WASENDER_API_KEY
   docker exec notification-api env | grep WASENDER
   ```

2. Set it if missing:

   ```bash
   export WASENDER_API_KEY=your-actual-key
   docker restart notification-api
   ```

3. Verify API key is valid and not expired

### Local Development Issues

**Port Conflicts:**

```bash
lsof -i :8080  # API
lsof -i :8081  # Email Worker
lsof -i :8082  # WhatsApp Worker
lsof -i :5433  # PostgreSQL
lsof -i :9092  # Kafka
```

**Database Connection:**

- Ensure using correct port: `5433` for Docker, `5432` for local PostgreSQL
- Verify connection string in `application-local.yml` when using `local` profile

**Kafka Connection:**

- Wait for Kafka to be fully ready (can take 30-60 seconds)
- Check Kafka logs: `docker compose logs kafka`

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

## 📊 Monitoring

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

## 📚 Documentation

- **OpenAPI/Swagger UI**: http://localhost:8080/swagger-ui.html
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture, design patterns, and technical details
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
