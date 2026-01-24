# Operations

## Local development (Docker)

```bash
# Infra only
docker compose up -d postgres zookeeper kafka

# Build and run API + workers locally, or:
docker compose up -d
```

- **Postgres**: localhost:5433, user `notification_user`, db `notification_db`, password `notification_pass`.
- **Kafka**: localhost:9092 (external); internal `kafka:29092` for container-to-container.
- **API**: 8080. **Email worker**: headless by default. **WhatsApp worker**: 8082. **Kafka UI**: 8089.

Migrations run from `deployment/` in numeric order via init-db volumes. See `deployment/MIGRATION_ORDER.md` for dependencies.

## Environment variables (main ones)

| Where | Variable | Purpose |
|-------|----------|---------|
| All | SPRING_DATASOURCE_URL, USERNAME, PASSWORD | DB connection |
| All | SPRING_KAFKA_BOOTSTRAP_SERVERS | Kafka (e.g. kafka:29092 in Docker, localhost:9092 local) |
| API | (topic names) | spring.kafka.topics.email / whatsapp / *-dlq (defaults in code) |
| email-worker | sendgrid.api.key, sendgrid.from.email/name | SendGrid fallback when no DB config |
| whatsapp-worker | wasender.api.base-url | WASender base URL |

Credentials for production are typically in DB (sendgrid_config, frappe_sites, whatsapp_sessions), not only env.

## Metrics

- **Endpoint**: `/actuator/prometheus` on each service.
- **Useful counters**: `notification.messages.sent`, `notification.messages.delivered`, `notification.messages.failed`, `notification.messages.dlq`, `notification.messages.retried`, by channel.
- **Latency**: `notification.kafka.publish.latency` by channel.

Interpret “sent” as “accepted by API”. Delivery and failure come from worker updates and history.

## Common runtime failures

| Symptom | Meaning / check |
|--------|------------------|
| Message stays PENDING | Worker not consuming or crash; check consumer group and logs. |
| Message goes FAILED (CONSUMER) | Worker attempted send; provider or config failed. See `message_logs.error_message`. KafkaRetryService will retry up to max, then DLQ. |
| Message goes FAILED (KAFKA) | Publish to Kafka failed. KafkaRetryService will retry republish. |
| “Tenant isolation violation” in log | Payload `siteId` ≠ `message_logs.site_id` (or null mismatch). Indicates bug or tampered payload. |
| “API key not configured” / 401 from provider | Credential missing or invalid for that site/session. Check DB config and provider dashboard. |



