# Extensibility

## Adding a new provider (same channel)

Example: SendGrid → SES, or WASender → Meta/Twilio.

**Change only:**

- The **worker** for that channel: replace the concrete send implementation (e.g. SendGridService / WasenderService) with a new one implementing the same `EmailProvider` / `WhatsAppProvider` contract. Point config (URL, keys) at the new provider.
- **notification-api**: Only if the API today supplies provider-specific defaults (e.g. from-address for email). Then either add a small “default from” abstraction or swap the implementation that fills those fields. The Kafka payload shape and topic layout stay as-is.

**Do not change:**

- Kafka topic names or payload schema for the channel.
- Tenant model: `siteId` and payload vs `message_logs.site_id` checks stay.
- Retry ownership (KafkaRetryService) or the rule that workers never mutate `retry_count` or produce to DLQ.
- Placement of credential resolution: it must remain inside the worker and never in the API or in the payload.

## Adding a new channel

- Add topic(s) and, if needed, DLQ.
- Add a worker that consumes that topic, does tenant check, resolves credentials inside the worker, sends, updates `message_logs`.
- In the API, extend channel enum and topic selection so new requests publish to the new topic. Reuse or extend `NotificationPayload` so it stays provider-neutral and secret-free.

## What must not change when adding providers

- **Payload**: No secrets, no provider-specific fields that leak keys or tokens.
- **Tenant checks**: Always validate `payload.siteId` against `message_logs.site_id` (or documented null semantics) before resolve/send.
- **Retry/DLQ**: Single authority (KafkaRetryService); workers only set FAILED and ack.
- **Credentials**: Resolved only in workers from DB (and, where designed, env fallback). Never read from the Kafka message body.

