# Notification System - High Throughput Multi-Tenant Microservice

A production-grade, multi-tenant notification platform built with Spring Boot, Kafka, and gRPC, supporting WhatsApp (via WASender) and Email (via SendGrid) delivery.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    External Clients                          │
│              (Frappe, External Systems)                     │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS/REST
┌──────────────────────▼──────────────────────────────────────┐
│                  Notification API                           │
│  ┌────────────────────────────────────────────────────┐   │
│  │  REST Controllers + Authentication + Kafka Producer│   │
│  └────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ Kafka Topics
        ┌──────────────┴──────────────┐
        │                             │
┌───────▼────────┐          ┌────────▼────────┐
│  Email Worker  │          │ WhatsApp Worker  │
│  (SendGrid)    │          │  (WASender)      │
└───────┬────────┘          └────────┬────────┘
        │                             │
        └──────────────┬──────────────┘
                       │ JDBC
        ┌──────────────▼──────────────┐
        │      PostgreSQL Database     │
        │  - frappe_sites             │
        │  - message_logs             │
        │  - site_metrics_daily       │
        └──────────────────────────────┘
```

### Components

1. **notification-api**: REST API gateway with authentication and Kafka message dispatcher
2. **email-worker**: Kafka consumer that sends emails via SendGrid
3. **whatsapp-worker**: Kafka consumer that sends WhatsApp messages via WASender
4. **common-proto**: Shared gRPC protocol buffer definitions

### Data Flow

1. **Client** sends notification request to API with `X-Site-Key` header
2. **API** validates API key, creates message log (status: PENDING), publishes to Kafka topic
3. **Worker** consumes from Kafka, sends via provider (SendGrid/WASender)
4. **Worker** updates message log status (SENT → DELIVERED or FAILED)
5. **Metrics** are automatically aggregated daily via database triggers

### Security

- **API Key Authentication**: BCrypt hashed keys (12 rounds)
- **Site Isolation**: Each site can only access its own data
- **One-time Key Generation**: API keys shown only once during registration

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Maven 3.9+
- Java 17+
- SendGrid API Key
- WASender API Key

### Environment Variables

Create a `.env` file in the root directory:

```bash
SENDGRID_API_KEY=your-sendgrid-api-key
SENDGRID_FROM_EMAIL=noreply@yourdomain.com
SENDGRID_FROM_NAME=Your Company
WASENDER_API_KEY=your-wasender-api-key
```

### Running with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

### Running Locally

1. **Start Infrastructure**:
```bash
docker-compose up -d postgres zookeeper kafka
```

2. **Build Common Proto**:
```bash
cd common-proto
mvn clean install
cd ..
```

3. **Run Services**:
```bash
# Terminal 1 - API
cd notification-api
mvn spring-boot:run

# Terminal 2 - Email Worker
cd email-worker
mvn spring-boot:run

# Terminal 3 - WhatsApp Worker
cd whatsapp-worker
mvn spring-boot:run
```

## 📚 Documentation

- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Complete deployment guide (Docker, local, production)
- **[API.md](API.md)** - Complete API reference documentation
- **[GITFLOW.md](GITFLOW.md)** - GitFlow workflow guide

## 📡 Quick API Examples

See [API.md](API.md) for complete API documentation.

### Register Site

```bash
curl -X POST http://localhost:8080/api/v1/site/register \
  -H "Content-Type: application/json" \
  -d '{"siteName": "my-site"}'
```

### Send Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "+1234567890",
    "body": "Hello!"
  }'
```

## 🔐 Security

- **API Key Authentication**: All requests (except registration) require `X-Site-Key` header
- **Key Hashing**: API keys are hashed using BCrypt (12 rounds) before storage
- **One-time Generation**: API keys are generated once during registration
- **Site Isolation**: Each site can only access its own metrics and logs

## 📊 Monitoring

### Prometheus Metrics

All services expose Prometheus metrics at `/actuator/prometheus`:

- `notification_messages_total`: Total messages processed
- `notification_messages_success`: Successfully delivered messages
- `notification_messages_failed`: Failed messages
- `kafka_messages_consumed`: Kafka consumer lag

### Health Checks

- API: `http://localhost:8080/actuator/health`
- Email Worker: `http://localhost:8081/actuator/health`
- WhatsApp Worker: `http://localhost:8082/actuator/health`

### Kafka UI

Access Kafka UI at `http://localhost:8089` to monitor topics and consumer groups.

## 🗄️ Database Schema

### Tables

- **frappe_sites**: Registered sites with API keys
- **message_logs**: All notification attempts with status tracking
- **site_metrics_daily**: Aggregated daily metrics per site and channel

### Enums

- `delivery_status`: PENDING, SENT, DELIVERED, FAILED, BOUNCED, REJECTED
- `notification_channel`: EMAIL, WHATSAPP, SMS, PUSH

## 🔄 Message Flow

1. **Client** sends notification request to API with `X-Site-Key`
2. **API** validates key, creates message log, publishes to Kafka topic
3. **Worker** consumes from Kafka, sends via provider (SendGrid/WASender)
4. **Worker** updates message log status (SENT/DELIVERED/FAILED)
5. **Metrics** are automatically aggregated daily via database triggers

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

## 📝 Configuration

### Application Properties

Each service has its own `application.yml`:

- **notification-api**: Port 8080, Kafka producer config
- **email-worker**: Port 8081, SendGrid config, Kafka consumer config
- **whatsapp-worker**: Port 8082, WASender config, Kafka consumer config

### Kafka Topics

- `notifications-email`: Email notifications queue
- `notifications-whatsapp`: WhatsApp notifications queue
- `notifications-email-dlq`: Dead letter queue for failed emails
- `notifications-whatsapp-dlq`: Dead letter queue for failed WhatsApp messages

## 🚨 Error Handling

- **Retry Logic**: Automatic retry with exponential backoff (max 3 retries)
- **DLQ**: Failed messages after max retries are sent to Dead Letter Queue
- **Logging**: Comprehensive logging at all levels
- **Status Tracking**: All message states tracked in database

## 📈 Performance

- **Async Processing**: Kafka-based async message processing
- **Batch Operations**: Bulk notification support
- **Connection Pooling**: Database connection pooling configured
- **Kafka Batching**: Producer batching enabled for throughput

## 🔧 Troubleshooting

### Kafka Connection Issues

```bash
# Check Kafka is running
docker-compose ps kafka

# View Kafka logs
docker-compose logs kafka

# Check topics
docker exec -it notification-kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Database Connection Issues

```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Connect to database
docker exec -it notification-postgres psql -U notification_user -d notification_db
```

### Worker Not Processing Messages

1. Check worker logs: `docker-compose logs email-worker`
2. Verify Kafka consumer group: Check Kafka UI
3. Verify API keys are configured correctly
4. Check message logs table for error messages

## 📚 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [SendGrid API](https://docs.sendgrid.com/)
- [WASender API](https://wasenderapi.com/docs)

## 📄 License

This project is proprietary software.

## 👥 Support

For issues and questions, please contact the development team.

