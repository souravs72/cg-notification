# System Architecture

This document provides a comprehensive overview of the Notification System architecture, design patterns, and implementation details for developers.

## 🏗️ Architecture Overview

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

## 📦 Components

### 1. notification-api

REST API gateway that handles client requests, authenticates sites, and dispatches messages to Kafka.

**Key Responsibilities:**
- Handle HTTP requests from external clients
- Authenticate requests using API keys (`X-Site-Key` header)
- Validate and transform notification requests
- Create message logs in database with status `PENDING`
- Publish messages to appropriate Kafka topics (`notifications-email` or `notifications-whatsapp`)
- Provide metrics and health endpoints

**Technology Stack:**
- Spring Boot 3.x
- Spring Security (API key authentication)
- Kafka Producer (async message dispatch)
- PostgreSQL (via JDBC for message logs)
- Spring Actuator (monitoring)

**Key Classes:**
- `NotificationController` - REST endpoints
- `ApiKeyAuthenticationFilter` - Authentication interceptor
- `NotificationService` - Business logic
- `KafkaNotificationProducer` - Kafka message publisher

### 2. email-worker

Kafka consumer that processes email notifications via SendGrid.

**Key Responsibilities:**
- Consume messages from `notifications-email` topic
- Send emails using SendGrid API
- Update message log status (`SENT` → `DELIVERED` or `FAILED`)
- Handle retries with exponential backoff (max 3 attempts)
- Publish failed messages to DLQ (`notifications-email-dlq`) after max retries

**Technology Stack:**
- Spring Boot 3.x
- Kafka Consumer (manual acknowledgment)
- SendGrid SDK
- PostgreSQL (via JDBC for status updates)

**Key Classes:**
- `EmailNotificationConsumer` - Kafka consumer
- `SendGridService` - SendGrid integration
- `MessageLogService` - Status updates

### 3. whatsapp-worker

Kafka consumer that processes WhatsApp notifications via WASender.

**Key Responsibilities:**
- Consume messages from `notifications-whatsapp` topic
- Send WhatsApp messages using WASender API
- Update message log status
- Handle retries and failures
- Publish failed messages to DLQ (`notifications-whatsapp-dlq`)

**Technology Stack:**
- Spring Boot 3.x
- Kafka Consumer (manual acknowledgment)
- WASender SDK
- PostgreSQL (via JDBC for status updates)

**Key Classes:**
- `WhatsAppNotificationConsumer` - Kafka consumer
- `WasenderService` - WASender integration
- `MessageLogService` - Status updates

### 4. common-proto

Shared gRPC protocol buffer definitions for inter-service communication (currently used for future extensibility).

## 🔄 Data Flow

### High-Level Flow

1. **Client Request**: External client sends notification request to API with `X-Site-Key` header
2. **Authentication**: API validates API key against `frappe_sites` table (BCrypt hash comparison)
3. **Message Log Creation**: API creates entry in `message_logs` table with status `PENDING`
4. **Kafka Publishing**: API publishes message to appropriate Kafka topic (`notifications-email` or `notifications-whatsapp`)
5. **Worker Consumption**: Worker consumes message from Kafka topic
6. **Provider Delivery**: Worker sends message via provider (SendGrid/WASender)
7. **Status Update**: Worker updates `message_logs` status:
   - `SENT` → when message is sent to provider
   - `DELIVERED` → when provider confirms delivery
   - `FAILED` → when delivery fails after retries
8. **Metrics Aggregation**: Database triggers automatically aggregate metrics into `site_metrics_daily` table

### Detailed Message Flow

```
Client → API → Kafka → Worker → Provider → Database
  │       │      │       │         │          │
  │       │      │       │         │          └─ Status Update
  │       │      │       │         └─ Delivery Confirmation
  │       │      │       └─ Send Message
  │       │      └─ Publish to Topic
  │       └─ Validate & Log
  └─ HTTP Request
```

### Example: Sending an Email

```java
// 1. Client sends request
POST /api/v1/notifications/send
Headers: X-Site-Key: abc123...
Body: { channel: "EMAIL", recipient: "user@example.com", ... }

// 2. API validates API key
ApiKeyAuthenticationFilter.validate("abc123...")
→ Query: SELECT * FROM frappe_sites WHERE api_key_hash = ?

// 3. API creates message log
INSERT INTO message_logs (site_id, channel, recipient, status, ...)
VALUES (1, 'EMAIL', 'user@example.com', 'PENDING', ...)

// 4. API publishes to Kafka
kafkaProducer.send("notifications-email", message)

// 5. Email worker consumes
EmailNotificationConsumer.onMessage(message)

// 6. Worker sends via SendGrid
sendGridService.send(email)

// 7. Worker updates status
UPDATE message_logs SET status = 'SENT', sent_at = NOW() WHERE id = ?

// 8. Provider callback (async)
UPDATE message_logs SET status = 'DELIVERED', delivered_at = NOW() WHERE id = ?
```

## 🔐 Security Architecture

### API Key Authentication

- **Storage**: API keys are hashed using BCrypt (12 rounds) before storage in `frappe_sites.api_key_hash`
- **Validation**: On each request, API key is validated against hashed value in database
- **One-time Generation**: API keys are generated once during site registration and shown only once
- **Site Isolation**: Each site can only access its own data (messages, metrics) via site_id filtering

### Security Layers

1. **Transport Layer**: HTTPS for all external communications (production)
2. **Authentication Layer**: API key validation on every request via `ApiKeyAuthenticationFilter`
3. **Authorization Layer**: Site-based data isolation (queries filtered by site_id)
4. **Data Layer**: Encrypted database connections (SSL/TLS)

### Example: Authentication Flow

```java
@PreAuthorize("hasRole('SITE')")
public class NotificationController {
    
    @PostMapping("/notifications/send")
    public ResponseEntity<NotificationResponse> sendNotification(
            @RequestHeader("X-Site-Key") String apiKey,
            @RequestBody NotificationRequest request) {
        // ApiKeyAuthenticationFilter already validated the key
        // Site context is available via SecurityContext
        Site site = getCurrentSite(); // From SecurityContext
        // ... process notification
    }
}
```

## 💾 Database Schema

### Core Tables

#### frappe_sites

Stores registered sites with their API keys.

```sql
CREATE TABLE frappe_sites (
    id UUID PRIMARY KEY,
    site_name VARCHAR(255) UNIQUE NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    whatsapp_session_name VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

**Key Fields:**
- `api_key_hash`: BCrypt hashed API key (never store plain text)
- `whatsapp_session_name`: Optional WhatsApp session identifier
- `is_active`: Enable/disable site access

#### message_logs

Tracks all notification attempts with status.

```sql
CREATE TABLE message_logs (
    id UUID PRIMARY KEY,
    message_id VARCHAR(50) UNIQUE NOT NULL,
    site_id UUID REFERENCES frappe_sites(id),
    channel VARCHAR(20) NOT NULL, -- EMAIL, WHATSAPP, SMS, PUSH
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL, -- PENDING, SENT, DELIVERED, FAILED, BOUNCED, REJECTED
    provider_response TEXT,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP
);
```

**Status Flow:**
```
PENDING → SENT → DELIVERED
         ↓
       FAILED (after retries)
```

#### site_metrics_daily

Aggregated daily metrics per site and channel (auto-populated by triggers).

```sql
CREATE TABLE site_metrics_daily (
    id UUID PRIMARY KEY,
    site_id UUID REFERENCES frappe_sites(id),
    channel VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    total_sent INT DEFAULT 0,
    total_delivered INT DEFAULT 0,
    total_failed INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(site_id, channel, date)
);
```

### Enums

- **delivery_status**: `PENDING`, `SENT`, `DELIVERED`, `FAILED`, `BOUNCED`, `REJECTED`
- **notification_channel**: `EMAIL`, `WHATSAPP`, `SMS`, `PUSH`

### Database Triggers

Automatic daily aggregation of metrics via PostgreSQL triggers:

```sql
-- Example trigger (simplified)
CREATE TRIGGER aggregate_metrics_trigger
AFTER INSERT OR UPDATE ON message_logs
FOR EACH ROW
WHEN (NEW.status IN ('DELIVERED', 'FAILED'))
EXECUTE FUNCTION aggregate_daily_metrics();
```

## ⚙️ Configuration

### Kafka Topics

- `notifications-email`: Email notifications queue
- `notifications-whatsapp`: WhatsApp notifications queue
- `notifications-email-dlq`: Dead letter queue for failed emails
- `notifications-whatsapp-dlq`: Dead letter queue for failed WhatsApp messages

### Kafka Configuration

**Producer (API):**
- Batching enabled for throughput optimization
- Partitioning based on `site_id` for ordering guarantees
- Replication factor: 3 (production)

**Consumer (Workers):**
- Manual acknowledgment for reliability
- Consumer groups: `email-worker-group`, `whatsapp-worker-group`
- Max retries: 3 with exponential backoff

### Application Properties

Each service has its own `application.yml`:

**notification-api:**
- Port: 8080
- Kafka producer configuration
- Database connection settings
- Security configuration

**email-worker:**
- Port: 8081
- SendGrid API configuration
- Kafka consumer configuration
- Retry and DLQ settings

**whatsapp-worker:**
- Port: 8082
- WASender API configuration
- Kafka consumer configuration
- Retry and DLQ settings

## 🔄 Error Handling & Resilience

### Retry Strategy

- **Exponential Backoff**: Automatic retry with exponential backoff (1s, 2s, 4s)
- **Max Retries**: 3 attempts before moving to DLQ
- **Retryable Errors**: Network errors, temporary provider failures (5xx)
- **Non-Retryable Errors**: Invalid recipient, authentication failures (4xx)

### Dead Letter Queue (DLQ)

Failed messages after max retries are sent to DLQ:
- `notifications-email-dlq` for email failures
- `notifications-whatsapp-dlq` for WhatsApp failures

DLQ messages can be manually reviewed and reprocessed via admin dashboard.

### Status Tracking

All message states are tracked in database:

| Status | Description | Next Possible States |
|--------|-------------|---------------------|
| `PENDING` | Message created, not yet sent to provider | `SENT`, `FAILED` |
| `SENT` | Message sent to provider, awaiting confirmation | `DELIVERED`, `FAILED`, `BOUNCED`, `REJECTED` |
| `DELIVERED` | Provider confirmed successful delivery | (final) |
| `FAILED` | Delivery failed after all retries | (final) |
| `BOUNCED` | Email bounced (email-specific) | (final) |
| `REJECTED` | Message rejected by provider | (final) |

### Example: Retry Logic

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2),
    dltStrategy = DltStrategy.FAIL_ON_ERROR
)
@KafkaListener(topics = "notifications-email")
public void consumeEmailNotification(EmailNotificationMessage message) {
    try {
        sendGridService.send(message);
        messageLogService.updateStatus(message.getId(), Status.SENT);
    } catch (RetryableException e) {
        // Will retry automatically
        throw e;
    } catch (NonRetryableException e) {
        // Will go directly to DLQ
        messageLogService.updateStatus(message.getId(), Status.FAILED);
        throw e;
    }
}
```

## ⚡ Performance Optimization

### Async Processing

- Kafka-based async message processing decouples API from workers
- Non-blocking request handling in API (returns immediately after Kafka publish)
- Parallel processing of messages by workers

### Batch Operations

- Bulk notification support (`POST /api/v1/notifications/send/bulk`)
- Kafka producer batching for improved throughput
- Database batch inserts for message logs

### Connection Pooling

- Database connection pooling (HikariCP) configured for optimal resource usage
- HTTP client connection pooling for provider APIs
- Kafka connection reuse

### Scalability

- **Horizontal Scaling**: Workers can be scaled independently (multiple instances)
- **Partitioning**: Kafka topics partitioned for parallel processing
- **Load Distribution**: Multiple worker instances share consumer groups

**Example: Scaling Workers**

```bash
# Scale email worker to 3 instances
docker compose up -d --scale email-worker=3

# Each instance will consume from different partitions
# Consumer group ensures no duplicate processing
```

## 📊 Monitoring & Observability

### Prometheus Metrics

All services expose Prometheus metrics at `/actuator/prometheus`:

- `notification_messages_total`: Total messages processed
- `notification_messages_success`: Successfully delivered messages
- `notification_messages_failed`: Failed messages
- `kafka_messages_consumed`: Kafka consumer lag
- `kafka_messages_produced`: Kafka producer throughput
- `database_connection_pool_active`: Active database connections
- `http_client_requests_total`: HTTP requests to providers

### Health Checks

- **API**: `http://localhost:8080/actuator/health`
- **Email Worker**: `http://localhost:8081/actuator/health`
- **WhatsApp Worker**: `http://localhost:8082/actuator/health`

Health checks include:
- Database connectivity
- Kafka connectivity
- Provider API availability

### Kafka UI

Access Kafka UI at `http://localhost:8089` to monitor:
- Topic partitions and offsets
- Consumer group lag
- Message throughput
- DLQ message counts

### Distributed Tracing

- Correlation IDs for request tracking across services
- Log aggregation for end-to-end visibility
- Performance metrics per operation

## 🏛️ Design Patterns

### Used Patterns

1. **Producer-Consumer Pattern**: API produces messages, workers consume
2. **Repository Pattern**: Data access abstraction (`MessageLogRepository`, `SiteRepository`)
3. **Service Layer Pattern**: Business logic separation (`NotificationService`, `SendGridService`)
4. **Filter Pattern**: Authentication via `ApiKeyAuthenticationFilter`
5. **Strategy Pattern**: Different providers (SendGrid, WASender) implement same interface

### Example: Service Layer Pattern

```java
@Service
public class NotificationService {
    private final MessageLogRepository messageLogRepository;
    private final KafkaNotificationProducer kafkaProducer;
    
    public NotificationResponse sendNotification(NotificationRequest request, Site site) {
        // 1. Create message log
        MessageLog log = createMessageLog(request, site);
        
        // 2. Publish to Kafka
        kafkaProducer.send(request.getChannel(), log);
        
        // 3. Return response
        return NotificationResponse.builder()
            .messageId(log.getMessageId())
            .status(log.getStatus())
            .build();
    }
}
```

## 🔧 Low-Level Design (LLD)

### API Request Processing

1. **Request Interceptor**: `ApiKeyAuthenticationFilter` validates `X-Site-Key` header
2. **Authentication Service**: Verifies API key against database (BCrypt comparison)
3. **Validation**: Validates request payload (channel, recipient, etc.)
4. **Message Service**: Creates message log entry with status `PENDING`
5. **Kafka Producer**: Publishes to appropriate topic
6. **Response**: Returns message ID to client

### Worker Message Processing

1. **Kafka Consumer**: Polls messages from topic
2. **Message Deserialization**: Converts Kafka message to domain object
3. **Provider Client**: Sends message via provider API (SendGrid/WASender)
4. **Status Update**: Updates message log in database (`SENT` → `DELIVERED`)
5. **Acknowledgment**: Commits Kafka offset
6. **Error Handling**: Retries or sends to DLQ on failure

### Database Transaction Management

- **API**: Transaction for message log creation (ensures consistency)
- **Workers**: Transaction for status updates (ensures atomicity)
- **Isolation**: Read committed for consistency
- **Rollback**: Automatic rollback on errors

## 🎯 High-Level Design (HLD)

### System Boundaries

- **External**: Clients (Frappe, external systems)
- **API Layer**: REST API gateway
- **Message Queue**: Kafka for async processing
- **Worker Layer**: Email and WhatsApp workers
- **Data Layer**: PostgreSQL database
- **External Services**: SendGrid, WASender

### Service Communication

- **Synchronous**: Client → API (REST/HTTPS)
- **Asynchronous**: API → Workers (Kafka)
- **Synchronous**: Workers → Providers (HTTPS)
- **Synchronous**: All services → Database (JDBC)

### Data Consistency

- **Eventual Consistency**: Message status updates are eventually consistent
- **Idempotency**: Message processing is idempotent (can be safely retried)
- **At-Least-Once Delivery**: Kafka guarantees at-least-once delivery
- **Status Tracking**: Database tracks all state transitions

### Scalability Considerations

- **Stateless API**: API instances can be scaled horizontally
- **Worker Scaling**: Workers scale based on Kafka partition count
- **Database Scaling**: Read replicas for metrics queries (future)
- **Kafka Scaling**: Topic partitioning for parallel processing

## 🚀 Future Enhancements

- Webhook support for delivery status callbacks
- SMS channel support
- Push notification support
- Rate limiting per site
- Advanced retry strategies
- Database read replicas for metrics
- Redis caching for API key lookups
- GraphQL API option
