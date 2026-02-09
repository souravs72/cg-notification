# Invariants and Rules

## Tenant isolation

- **Boundary**: `siteId` (UUID). Every row in `message_logs` is scoped by `site_id` (nullable only for “dashboard” sends).
- **Rule**: Before using credentials or sending, worker must enforce `payload.siteId == message_logs.site_id` (from DB). If payload has no `siteId`, `message_logs.site_id` must be null (email global-config path only).
- **WhatsApp extra**: If payload carries `whatsappSessionName`, it must match the site’s `whatsappSessionName` before using that session’s API key.

## Provider isolation

- **API**: Chooses topic by channel (EMAIL/WHATSAPP). Payload is generic; no provider SDK or API calls on the send path.
- **Workers**: All provider-specific code (SendGrid, WASender) and credential resolution live inside the worker. API never sees provider keys or responses.

## Kafka usage

- Publish only after DB commit (TransactionSynchronizationManager.afterCommit).
- Key = `messageId`; value = JSON only (no secrets).
- Consumers: fail fast, ack every message (success or failure). No in-consumer retries; KafkaRetryService owns retries.
- Topics are per channel; DLQ per channel. Do not mix channels in one topic.

## Credential handling

- **Never** put API keys, tokens, or auth headers in the Kafka payload or in logs.
- Resolve credentials only inside workers:
  - **Email**: site → frappe_sites.sendgrid_api_key → sendgrid_config → env fallback.
  - **WhatsApp**: siteId required → site → whatsappSessionName → whatsapp_sessions.session_api_key.
- Treat payload as untrusted for tenant scope: always validate against `message_logs` before resolve/send.

## Retry ownership

- **KafkaRetryService** (notification-api) is the only component that mutates `retry_count` and drives republish/DLQ.
- Workers only set status to FAILED (and `failure_type` CONSUMER where applicable). They must not increment `retry_count` or produce to DLQ.
- `failure_type`: KAFKA = publish failed; CONSUMER = worker processing failed. Retry and DLQ logic use this.

## Status and metrics semantics

- `notification.messages.sent` = “accepted by API” (persisted + queued to send), not “published to Kafka”. Kafka publish latency is tracked separately.
- StatusTransitionValidator must stay deterministic: no env flags, no DB calls, no time-based branch — pure enum-map validation.