# Local Deployment Instructions

## Prerequisites

1. **Docker Desktop** must be running
2. **Java 17+** installed
3. **Maven 3.9+** installed
4. **WASender API Key** (for WhatsApp)
5. **SendGrid API Key** (for email, optional for WhatsApp testing)

## Quick Start

### Step 1: Start Infrastructure

```bash
# Start PostgreSQL, Kafka, and Zookeeper
docker-compose up -d postgres zookeeper kafka

# Wait for services to be ready (about 30 seconds)
docker-compose ps
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
export SENDGRID_API_KEY=your-sendgrid-api-key  # Optional
```

### Step 4: Start Services

**Terminal 1 - Notification API:**
```bash
cd notification-api
mvn spring-boot:run
```

**Terminal 2 - WhatsApp Worker:**
```bash
cd whatsapp-worker
WASENDER_API_KEY=your-key mvn spring-boot:run
```

**Terminal 3 - Email Worker (Optional):**
```bash
cd email-worker
SENDGRID_API_KEY=your-key mvn spring-boot:run
```

### Step 5: Register a Site

```bash
curl -X POST http://localhost:8080/api/v1/site/register \
  -H "Content-Type: application/json" \
  -d '{
    "siteName": "test-site",
    "description": "Test site"
  }'
```

**Save the `apiKey` from the response!**

### Step 6: Send Test WhatsApp Message

```bash
# Using the test script
./test-whatsapp.sh YOUR_API_KEY 918576882906

# Or manually
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: YOUR_API_KEY" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "918576882906@s.whatsapp.net",
    "body": "Hello! This is a test message."
  }'
```

## Troubleshooting

### Docker Issues

If Docker daemon is not running:
```bash
# Start Docker Desktop, or
sudo systemctl start docker
```

### Database Connection Issues

Check if PostgreSQL is accessible:
```bash
docker exec -it notification-postgres psql -U notification_user -d notification_db
```

### Kafka Issues

Check Kafka topics:
```bash
docker exec -it notification-kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Service Health Checks

- API: http://localhost:8080/actuator/health
- WhatsApp Worker: http://localhost:8082/actuator/health
- Email Worker: http://localhost:8081/actuator/health

## Notes

- WhatsApp numbers must be in JID format: `countrycode+number@s.whatsapp.net`
- Example: `+918576882906` becomes `918576882906@s.whatsapp.net`
- Make sure your WASender session is connected before sending messages
