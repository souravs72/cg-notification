# Security and Logging

## What must never be logged

- API keys (SendGrid, WASender, PATs, session keys).
- Authorization or Bearer headers.
- Full HTTP request/response bodies from providers (they may contain tokens or keys).
- Passwords or hashes.
- Raw provider error payloads. Use status code + “response redacted” or parsed `message`/`error` fields only.

Safe to log: `siteId`, `messageId`, provider name, HTTP status codes, error category (AUTH/CONFIG/TEMPORARY), sanitized error text, session names (identifiers, not secrets), retry counts.

## Where secrets are resolved

- **Email**: In email-worker only. Order: site (frappe_sites.sendgrid_api_key) → sendgrid_config → env (`sendgrid.api.key`). Never from payload.
- **WhatsApp**: In whatsapp-worker only. Order: siteId → site → whatsappSessionName → whatsapp_sessions.session_api_key. Never from payload.
- **API**: Does not resolve or store provider API keys for send path. It may call SendGrid config for “from” defaults when building payload; that is the only provider-tied logic in the API on the send path.

## Error handling expectations

- Workers: on failure, set status FAILED and ack. Do not log or store raw provider response body in `error_message` or in logs. Use status, length, or “[redacted]” / “Provider error (details redacted)”.
- API/Admin: when surfacing provider errors to clients, use only parsed `message`/`error` or a generic “details redacted” string, never the raw response body.
- Exceptions that may wrap provider responses (e.g. WebClientResponseException): do not pass the exception message or body into log format strings. Log type + status (and “response redacted”) instead.



