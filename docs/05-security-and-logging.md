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

## Running behind an ALB (session & security basics)

- **TLS termination**: HTTPS terminates at the ALB; backend services see plain HTTP.
- **Forwarded headers**: Configure the API to trust `X-Forwarded-*` headers (for example `server.forward-headers-strategy=framework`) so redirects, CSRF/session logic, and generated links use the correct scheme and host.
- **HSTS**: Do **not** enable strict-transport-security headers from the app when an ALB already handles HTTPS; this can interact badly with HTTP between ALB and backend and contribute to redirect loops.
- **Session cookie**: Configure cookies explicitly for ALB scenarios, for example:
  - `same-site: lax` so redirects after login keep the session cookie.
  - `http-only: true` to prevent JavaScript access.
  - A reasonable `max-age` (for example 30 days) based on your UX/security trade-offs.
- **WAF and auth paths**: When placing AWS WAF in front of the ALB with managed rule sets, add a small allow rule for `/auth/**` so login/register are not blocked, and keep rate limiting enabled to protect them.
