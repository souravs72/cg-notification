# System Architecture

## Overview

The Notification System is a microservices-based platform designed for high-throughput, multi-tenant notification delivery. It follows event-driven architecture principles using Apache Kafka for asynchronous message processing.

## Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    External Clients                          │
│              (Frappe, External Systems)                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ HTTPS/REST
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                  Notification API                           │
│  ┌────────────────────────────────────────────────────┐   │
│  │  REST Controllers                                   │   │
│  │  - Site Registration                                │   │
│  │  - Notification Sending                             │   │
│  │  - Metrics Retrieval                                │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Authentication Layer                               │   │
│  │  - API Key Validation                               │   │
│  │  - Site Context Resolution                          │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Kafka Producers                                    │   │
│  │  - notifications-email                               │   │
│  │  - notifications-whatsapp                            │   │
│  └────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ Kafka Topics
                       │
        ┌──────────────┴──────────────┐
        │                             │
┌───────▼────────┐          ┌────────▼────────┐
│  Email Worker  │          │ WhatsApp Worker  │
│                │          │                  │
│ ┌───────────┐  │          │ ┌─────────────┐ │
│ │ Kafka     │  │          │ │ Kafka       │ │
│ │ Consumer  │  │          │ │ Consumer    │ │
│ └─────┬─────┘  │          │ └──────┬──────┘ │
│       │        │          │        │        │
│ ┌─────▼─────┐  │          │ ┌──────▼──────┐ │
│ │ SendGrid  │  │          │ │ WASender    │ │
│ │ Service   │  │          │ │ Service     │ │
│ └───────────┘  │          │ └─────────────┘ │
│                │          │                  │
│ ┌───────────┐  │          │ ┌─────────────┐ │
│ │ Log      │  │          │ │ Log         │ │
│ │ Service  │  │          │ │ Service      │ │
│ └──────────┘  │          │ └─────────────┘ │
└────────────────┘          └─────────────────┘
        │                             │
        └──────────────┬──────────────┘
                       │
                       │ JDBC
                       │
        ┌──────────────▼──────────────┐
        │      PostgreSQL Database     │
        │  ┌────────────────────────┐  │
        │  │ frappe_sites           │  │
        │  │ message_logs           │  │
        │  │ site_metrics_daily     │  │
        │  └────────────────────────┘  │
        └──────────────────────────────┘
```

## Data Flow

### 1. Site Registration Flow

```
Client → POST /api/v1/site/register
  ↓
SiteService.generateApiKey()
  ↓
SiteService.hashApiKey() [BCrypt]
  ↓
FrappeSite.save()
  ↓
Return API Key (one-time display)
```

### 2. Notification Sending Flow

```
Client → POST /api/v1/notifications/send
  ↓
Authentication: Validate X-Site-Key
  ↓
NotificationService.sendNotification()
  ↓
Create MessageLog (status: PENDING)
  ↓
Serialize to JSON
  ↓
KafkaTemplate.send(topic, messageId, payload)
  ↓
Return 202 ACCEPTED
```

### 3. Worker Processing Flow

```
Kafka Consumer receives message
  ↓
Deserialize NotificationPayload
  ↓
Update MessageLog (status: SENT)
  ↓
Call Provider API (SendGrid/WASender)
  ↓
If Success:
  Update MessageLog (status: DELIVERED)
  Acknowledge Kafka message
Else:
  Retry (max 3 times with exponential backoff)
  If still fails:
    Send to DLQ
    Update MessageLog (status: FAILED)
```

### 4. Metrics Aggregation Flow

```
Database Trigger (on message_logs INSERT)
  ↓
update_daily_metrics() function
  ↓
INSERT/UPDATE site_metrics_daily
  ↓
Aggregate by site_id, date, channel
```

## Security Architecture

### API Key Management

1. **Generation**: Cryptographically secure random 64-byte key, Base64 URL-encoded
2. **Storage**: BCrypt hash (12 rounds) stored in database
3. **Validation**: Constant-time comparison using BCrypt matcher
4. **Scope**: One API key per site, site isolation enforced

### Authentication Flow

```
Request with X-Site-Key header
  ↓
SiteService.validateApiKey()
  ↓
Query all sites (in production, use indexed lookup)
  ↓
BCrypt.matches(rawKey, hashedKey) for each site
  ↓
Return FrappeSite entity
  ↓
Set in request context
```

## Scalability Considerations

### Horizontal Scaling

- **API Service**: Stateless, can scale horizontally
- **Workers**: Multiple instances can consume from same Kafka topic (consumer groups)
- **Database**: Read replicas for metrics queries, connection pooling

### Performance Optimizations

1. **Kafka Batching**: Producer batching enabled
2. **Database Indexing**: Indexes on frequently queried columns
3. **Connection Pooling**: HikariCP configured
4. **Async Processing**: Non-blocking Kafka operations
5. **Batch Operations**: Bulk notification endpoint

## Resilience Patterns

### Retry Logic

- **Max Retries**: 3 attempts
- **Backoff Strategy**: Exponential (1s, 2s, 4s)
- **Retry Conditions**: Network errors, transient failures

### Dead Letter Queue

- Failed messages after max retries → DLQ topic
- Manual inspection and reprocessing possible
- Error details included in DLQ payload

### Circuit Breaker (Future Enhancement)

- Monitor provider API health
- Open circuit on repeated failures
- Fallback mechanisms

## Monitoring & Observability

### Metrics

- **Application Metrics**: Prometheus format
  - Message counts (total, success, failed)
  - Processing latency
  - Kafka consumer lag
  
- **Infrastructure Metrics**: 
  - Database connection pool stats
  - Kafka producer/consumer metrics
  - HTTP request metrics

### Logging

- **Structured Logging**: JSON format (production)
- **Log Levels**: DEBUG, INFO, WARN, ERROR
- **Correlation IDs**: Message ID for tracing

### Health Checks

- **Liveness**: Application is running
- **Readiness**: Dependencies (DB, Kafka) are available

## Database Schema

### Tables

1. **frappe_sites**: Site registration and API keys
2. **message_logs**: Complete audit trail of all notifications
3. **site_metrics_daily**: Pre-aggregated daily metrics

### Indexes

- Primary keys on all tables
- Foreign key indexes
- Query-specific indexes (site_id, channel, status, date)

### Triggers

- **Auto-update metrics**: Trigger on message_logs INSERT
- **Auto-update timestamps**: Triggers for updated_at columns

## Deployment Architecture

### Container Strategy

- Each service in separate container
- Multi-stage Docker builds
- Minimal runtime images (Alpine Linux)

### Service Discovery

- Docker Compose networking
- Environment-based configuration
- Health check endpoints

### Configuration Management

- Environment variables for secrets
- application.yml for defaults
- Externalized configuration support

## Future Enhancements

1. **gRPC Service**: Inter-service communication
2. **Rate Limiting**: Per-site rate limits
3. **Template Engine**: Message templating
4. **Webhooks**: Delivery status callbacks
5. **Multi-region**: Geographic distribution
6. **Caching**: Redis for API key lookups
7. **Message Scheduling**: Delayed delivery support

