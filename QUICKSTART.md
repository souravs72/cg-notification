# Quick Start Guide

## Prerequisites Check

```bash
# Check Java version (should be 17+)
java -version

# Check Maven version
mvn -version

# Check Docker
docker --version
docker-compose --version
```

## Step 1: Environment Setup

Create `.env` file in the root directory:

```bash
cat > .env << EOF
SENDGRID_API_KEY=your-sendgrid-api-key-here
SENDGRID_FROM_EMAIL=noreply@yourdomain.com
SENDGRID_FROM_NAME=Your Company Name
WASENDER_API_KEY=your-wasender-api-key-here
EOF
```

## Step 2: Start Infrastructure

```bash
# Start PostgreSQL, Kafka, and Zookeeper
docker-compose up -d postgres zookeeper kafka

# Wait for services to be healthy (about 30 seconds)
docker-compose ps
```

## Step 3: Build Common Proto Module

```bash
cd common-proto
mvn clean install
cd ..
```

## Step 4: Start Services

### Option A: Using Docker Compose (Recommended)

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f
```

### Option B: Running Locally

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

## Step 5: Register a Site

```bash
curl -X POST http://localhost:8080/api/v1/site/register \
  -H "Content-Type: application/json" \
  -d '{
    "siteName": "test-site",
    "description": "Test site for notifications"
  }'
```

**Save the `apiKey` from the response!**

## Step 6: Send a Test Notification

### Email Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: YOUR_API_KEY_HERE" \
  -d '{
    "channel": "EMAIL",
    "recipient": "test@example.com",
    "subject": "Test Email",
    "body": "This is a test email notification",
    "isHtml": false
  }'
```

### WhatsApp Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: YOUR_API_KEY_HERE" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "1234567890@s.whatsapp.net",
    "body": "Hello from WhatsApp!"
  }'
```

## Step 7: Check Metrics

```bash
# Get summary
curl -H "X-Site-Key: YOUR_API_KEY_HERE" \
  http://localhost:8080/api/v1/metrics/site/summary

# Get daily metrics
curl -H "X-Site-Key: YOUR_API_KEY_HERE" \
  "http://localhost:8080/api/v1/metrics/site/daily?startDate=2024-01-01&endDate=2024-01-31"
```

## Troubleshooting

### Services won't start

1. Check if ports are already in use:
```bash
# Check ports
netstat -tulpn | grep -E '8080|8081|8082|5432|9092'
```

2. Check Docker containers:
```bash
docker-compose ps
docker-compose logs [service-name]
```

### Kafka connection issues

```bash
# Check Kafka is running
docker exec -it notification-kafka kafka-topics --list --bootstrap-server localhost:9092

# Check consumer groups
docker exec -it notification-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

### Database connection issues

```bash
# Connect to database
docker exec -it notification-postgres psql -U notification_user -d notification_db

# Check tables
\dt

# Check message logs
SELECT * FROM message_logs ORDER BY created_at DESC LIMIT 10;
```

## Monitoring

- **Kafka UI**: http://localhost:8089
- **API Health**: http://localhost:8080/actuator/health
- **Email Worker Health**: http://localhost:8081/actuator/health
- **WhatsApp Worker Health**: http://localhost:8082/actuator/health
- **Prometheus Metrics**: http://localhost:8080/actuator/prometheus

## Next Steps

1. Configure your SendGrid account and verify sender email
2. Set up WASender WhatsApp session (see WASender documentation)
3. Set up monitoring dashboards (Grafana + Prometheus)
4. Configure production environment variables
5. Set up SSL/TLS certificates for production

