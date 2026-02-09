# Operations

## Local development (Docker)

```bash
# Full stack (Postgres, Redis, LocalStack SNS/SQS, init-sns-sqs, API, workers)
docker compose up -d
```

- **Postgres**: localhost:5433, user `notification_user`, db `notification_db`, password `notification_pass`.
- **LocalStack** (SNS/SQS): localhost:4566. Topics and queues are created by `init-sns-sqs` on startup.
- **API**: 8080. **Email worker**: 8081 (management). **WhatsApp worker**: 8082.

Migrations run from `deployment/` in numeric order via init-db volumes. See `deployment/MIGRATION_ORDER.md` for dependencies.

## Environment variables (main ones)

| Where | Variable | Purpose |
|-------|----------|---------|
| All | SPRING_DATASOURCE_URL, USERNAME, PASSWORD | DB connection |
| All | AWS_REGION | Region (e.g. us-east-1). For Docker: AWS_SNS_ENDPOINT, AWS_SQS_ENDPOINT → http://localstack:4566 |
| API | messaging.sns.topics.*, messaging.sqs.queues.* | SNS topic and SQS queue names (defaults in application.yml) |
| email-worker | sendgrid.api.key, sendgrid.from.email/name | SendGrid fallback when no DB config |
| whatsapp-worker | wasender.api.base-url | WASender base URL |

Credentials for production are typically in DB (sendgrid_config, frappe_sites, whatsapp_sessions), not only env.

## Metrics

- **Endpoint**: `/actuator/prometheus` on each service.
- **Useful counters**: `notification.messages.sent`, `notification.messages.delivered`, `notification.messages.failed`, `notification.messages.dlq`, `notification.messages.retried`, by channel.
- **Latency**: `notification.messaging.publish.latency` by channel (SNS publish).

Interpret “sent” as “accepted by API”. Delivery and failure come from worker updates and history.

## Common runtime failures

| Symptom | Meaning / check |
|--------|------------------|
| Message stays PENDING | Worker not consuming or crash; check SQS queue depth and worker logs. |
| Message goes FAILED (CONSUMER) | Worker attempted send; provider or config failed. See `message_logs.error_message`. MessagingRetryService will retry up to max, then DLQ. |
| Message goes FAILED (KAFKA) | Publish to SNS failed (failure_type KAFKA retained for DB). MessagingRetryService will retry republish. |
| “Tenant isolation violation” in log | Payload `siteId` ≠ `message_logs.site_id` (or null mismatch). Indicates bug or tampered payload. |
| “API key not configured” / 401 from provider | Credential missing or invalid for that site/session. Check DB config and provider dashboard.