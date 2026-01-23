# Cursor System Contract – Notification Platform

You are contributing to a production-grade, multi-tenant notification platform.

This is INFRASTRUCTURE software.
Correctness, safety, and clarity matter more than speed.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MANDATORY ARCHITECTURE RULES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. MULTI-TENANCY
- Every request, message, metric, and permission is scoped to site_id.
- site_id MUST be resolved from authentication context.
- site_id MUST NOT be accepted from request body, query params, or headers.
- Any code that bypasses tenant isolation is INVALID.

2. MODULE BOUNDARIES
- common module contains ONLY pure domain objects, enums, DTOs, constants.
- NO Spring beans, NO repositories, NO HTTP clients in common.
- Provider SDKs are allowed ONLY in worker-service.
- Controllers must never call providers directly.

3. ASYNC & RETRIES
- All message sending is asynchronous.
- Controllers persist intent and enqueue jobs only.
- Retry logic is centralized; never retry in controllers.
- AUTH failures are NEVER retried.

4. PROVIDER AGNOSTICISM
- Core logic must not reference provider names (SendGrid, Wasender, etc).
- Providers are accessed only via channel interfaces.
- Provider adapters must return normalized results.

5. API DISCIPLINE
- APIs are versioned (/v1).
- Endpoints must be idempotent.
- Errors must use explicit error codes.
- No silent exception handling.

6. DATA ACCESS
- Every DB table must include site_id.
- Every query must be tenant-scoped by default.
- No cross-tenant joins.
- No shared write ownership across services.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CODE QUALITY RULES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- Prefer explicit, readable code over clever abstractions.
- Small classes, single responsibility.
- No unused dependencies.
- No TODOs without context.
- All public methods must have clear intent.
- Tests are required for orchestration, retry logic, and tenant isolation.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ENFORCEMENT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

If a request violates ANY rule above:
- DO NOT generate code.
- Explain clearly which rule is violated.
- Suggest a compliant alternative.

You are expected to think before writing code.


## After code generation:

“Review this code against rules.md.
List any violations or risks.”