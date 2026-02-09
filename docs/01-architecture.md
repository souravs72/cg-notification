# Architecture

## Overview

```
Clients → Notification API → Kafka (notifications-email | notifications-whatsapp) → Workers → Providers (SendGrid / WASender)
                                    ↓
                            PostgreSQL (message_logs, frappe_sites, sendgrid_config, whatsapp_sessions, ...)
```

- **notification-api**: REST gateway. Authenticates (X-Site-Key → frappe_sites), writes `message_logs` (PENDING), publishes JSON payload to channel topic. Does not call providers.
- **email-worker**: Consumes `notifications-email`, resolves credentials from DB, sends via SendGrid, updates `message_logs`.
- **whatsapp-worker**: Consumes `notifications-whatsapp`, resolves credentials from DB, sends via WASender, updates `message_logs`.
- **common-proto**: Shared provider interfaces (`EmailProvider`, `WhatsAppProvider`) and result/retry types. No runtime dependency from API to workers.

## Module responsibilities

| Module | Owns | Does not do |
|--------|------|-------------|
| notification-api | Message log create, topic selection, Kafka produce, retry scheduling (KafkaRetryService) | Credential resolution, provider calls |
| email-worker | SendGrid calls, status update (DELIVERED/FAILED) for email | Retry count mutation, DLQ produce |
| whatsapp-worker | WASender calls, status update for WhatsApp | Retry count mutation, DLQ produce |

## Message flow

1. Request → API validates site key → insert `message_logs` (PENDING) → after commit: `kafkaTemplate.send(topic, messageId, payload)`.
2. Worker consumes by channel → parse payload → **tenant check**: `payload.siteId` must match `message_logs.site_id` (or both null for email “dashboard” path).
3. Worker resolves credentials (see [05-security-and-logging](05-security-and-logging.md)) → provider.send() → on success: update status DELIVERED; on failure: update status FAILED, ack.
4. KafkaRetryService (API) periodically retries FAILED rows (by `failure_type`: KAFKA or CONSUMER), republishes to same topic or, after max retries, to DLQ.

## Kafka topics

| Topic | Purpose |
|-------|---------|
| notifications-email | Email message queue |
| notifications-whatsapp | WhatsApp message queue |
| notifications-email-dlq | Dead letter for email |
| notifications-whatsapp-dlq | Dead letter for WhatsApp |

Key = `messageId`. Value = JSON: `messageId`, `siteId`, `channel`, `recipient`, `subject`, `body`, `fromEmail`, `fromName`, `whatsappSessionName`, media fields, `metadata`. No API keys or secrets in the payload.

## DB ownership

- **notification-api**: Inserts and owns `retry_count`, `failure_type`, status transitions for retry/DLQ logic.
- **Workers**: Only UPDATE status, `error_message`, `sent_at`, `delivered_at`, `failure_type` for consumed messages. Do not mutate `retry_count`.



