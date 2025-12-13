# API Documentation

Complete API reference for the Notification System.

## Table of Contents

- [Authentication](#authentication)
- [Site Management](#site-management)
- [Notifications](#notifications)
- [Message Logs](#message-logs)
- [Metrics](#metrics)
- [Admin Dashboard](#admin-dashboard)

## Authentication

All API endpoints (except site registration) require authentication via the `X-Site-Key` header.

```bash
curl -H "X-Site-Key: your-api-key" \
  http://localhost:8080/api/v1/metrics/site/summary
```

## Site Management

### Register Site

Register a new Frappe site and receive an API key.

**Endpoint:** `POST /api/v1/site/register`

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/site/register \
  -H "Content-Type: application/json" \
  -d '{
    "siteName": "my-frappe-site",
    "description": "Production site"
  }'
```

**Response:**
```json
{
  "siteId": "uuid",
  "siteName": "my-frappe-site",
  "apiKey": "generated-api-key-here",
  "message": "Site registered successfully"
}
```

⚠️ **IMPORTANT**: Save the `apiKey` securely - it's only shown once!

## Notifications

### Send Single Notification

**Endpoint:** `POST /api/v1/notifications/send`

**Headers:**
- `X-Site-Key`: Your API key
- `Content-Type`: application/json

#### Email Notification

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Welcome!",
    "body": "Welcome to our service",
    "isHtml": true,
    "fromEmail": "noreply@yourdomain.com",
    "fromName": "Your Company"
  }'
```

#### WhatsApp Text Message

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "+1234567890",
    "body": "Hello from WhatsApp!"
  }'
```

#### WhatsApp with Image

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "+1234567890",
    "imageUrl": "https://example.com/image.jpg",
    "caption": "Check out this image!"
  }'
```

#### WhatsApp with Document

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "+1234567890",
    "documentUrl": "https://example.com/document.pdf",
    "fileName": "document.pdf",
    "caption": "Please review this document"
  }'
```

**Response:**
```json
{
  "messageId": "MSG-abc123",
  "status": "PENDING",
  "message": "Notification queued successfully"
}
```

### Send Bulk Notifications

**Endpoint:** `POST /api/v1/notifications/send/bulk`

```bash
curl -X POST http://localhost:8080/api/v1/notifications/send/bulk \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "notifications": [
      {
        "channel": "EMAIL",
        "recipient": "user1@example.com",
        "subject": "Hello",
        "body": "Message 1"
      },
      {
        "channel": "WHATSAPP",
        "recipient": "+1234567890",
        "body": "Message 2"
      }
    ]
  }'
```

**Response:**
```json
{
  "totalQueued": 2,
  "messageIds": ["MSG-abc123", "MSG-def456"],
  "message": "Bulk notifications queued successfully"
}
```

### Schedule Notification

**Endpoint:** `POST /api/v1/notifications/schedule`

```bash
curl -X POST http://localhost:8080/api/v1/notifications/schedule \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Scheduled Email",
    "body": "This will be sent later",
    "scheduledAt": "2024-12-25T10:00:00Z"
  }'
```

## Message Logs

### List Messages

**Endpoint:** `GET /api/v1/messages/logs`

**Query Parameters:**
- `status` (optional): Filter by status (PENDING, SENT, DELIVERED, FAILED, BOUNCED, REJECTED)
- `channel` (optional): Filter by channel (EMAIL, WHATSAPP)
- `startDate` (optional): Start date (YYYY-MM-DD)
- `endDate` (optional): End date (YYYY-MM-DD)
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Example:**
```bash
curl -H "X-Site-Key: your-api-key" \
  "http://localhost:8080/api/v1/messages/logs?status=DELIVERED&channel=EMAIL&page=0&size=10"
```

**Response:**
```json
{
  "messages": [
    {
      "id": "uuid",
      "messageId": "MSG-ABC123",
      "siteId": "uuid",
      "siteName": "My Site",
      "channel": "EMAIL",
      "status": "DELIVERED",
      "recipient": "user@example.com",
      "subject": "Welcome",
      "body": "Welcome message content",
      "fromEmail": "noreply@example.com",
      "fromName": "Notification Service",
      "isHtml": true,
      "sentAt": "2024-01-01T10:00:00",
      "deliveredAt": "2024-01-01T10:00:05",
      "retryCount": 0,
      "metadata": {}
    }
  ],
  "totalElements": 100,
  "totalPages": 10,
  "currentPage": 0,
  "pageSize": 10
}
```

### Get Specific Message

**Endpoint:** `GET /api/v1/messages/logs/{messageId}`

```bash
curl -H "X-Site-Key: your-api-key" \
  "http://localhost:8080/api/v1/messages/logs/MSG-ABC123"
```

**Response:**
```json
{
  "id": "uuid",
  "messageId": "MSG-ABC123",
  "siteId": "uuid",
  "siteName": "My Site",
  "channel": "EMAIL",
  "status": "DELIVERED",
  "recipient": "user@example.com",
  "subject": "Welcome",
  "body": "Welcome message content",
  "errorMessage": null,
  "sentAt": "2024-01-01T10:00:00",
  "deliveredAt": "2024-01-01T10:00:05",
  "createdAt": "2024-01-01T09:59:50"
}
```

## Metrics

### Get Site Summary

**Endpoint:** `GET /api/v1/metrics/site/summary`

```bash
curl -H "X-Site-Key: your-api-key" \
  http://localhost:8080/api/v1/metrics/site/summary
```

**Response:**
```json
{
  "totalSent": 1250,
  "totalSuccess": 1180,
  "totalFailed": 70,
  "successRate": 94.4,
  "channelMetrics": {
    "EMAIL": {
      "channel": "EMAIL",
      "totalSent": 800,
      "totalSuccess": 760,
      "totalFailed": 40
    },
    "WHATSAPP": {
      "channel": "WHATSAPP",
      "totalSent": 450,
      "totalSuccess": 420,
      "totalFailed": 30
    }
  }
}
```

### Get Daily Metrics

**Endpoint:** `GET /api/v1/metrics/site/daily`

**Query Parameters:**
- `startDate` (required): Start date (YYYY-MM-DD)
- `endDate` (required): End date (YYYY-MM-DD)

```bash
curl -H "X-Site-Key: your-api-key" \
  "http://localhost:8080/api/v1/metrics/site/daily?startDate=2024-01-01&endDate=2024-01-31"
```

**Response:**
```json
{
  "dailyMetrics": [
    {
      "date": "2024-01-01",
      "totalSent": 50,
      "totalSuccess": 48,
      "totalFailed": 2,
      "channelMetrics": {
        "EMAIL": {
          "totalSent": 30,
          "totalSuccess": 29,
          "totalFailed": 1
        },
        "WHATSAPP": {
          "totalSent": 20,
          "totalSuccess": 19,
          "totalFailed": 1
        }
      }
    }
  ]
}
```

### Get Message Statistics

**Endpoint:** `GET /api/v1/messages/stats`

```bash
curl -H "X-Site-Key: your-api-key" \
  http://localhost:8080/api/v1/messages/stats
```

**Response:**
```json
{
  "totalMessages": 1250,
  "pending": 10,
  "scheduled": 5,
  "sent": 50,
  "delivered": 1150,
  "failed": 35,
  "successRate": 92.0,
  "averageMessagesPerDay": 41.67
}
```

## Admin Dashboard

### Dashboard Access

**URL:** http://localhost:8080/admin/dashboard

The admin dashboard provides:
- Overall system metrics
- Site-wise statistics
- Recent messages
- Success rate charts
- Channel distribution

### Admin API Endpoints

#### Get Dashboard Metrics

**Endpoint:** `GET /admin/api/metrics`

```bash
curl http://localhost:8080/admin/api/metrics
```

**Response:**
```json
{
  "totalSites": 5,
  "totalMessagesSent": 1250,
  "totalMessagesSuccess": 1180,
  "totalMessagesFailed": 70,
  "overallSuccessRate": 94.4,
  "siteMetrics": [
    {
      "siteId": "uuid",
      "siteName": "Site Name",
      "totalSent": 500,
      "totalSuccess": 480,
      "totalFailed": 20,
      "successRate": 96.0,
      "channelMetrics": {
        "EMAIL": {
          "totalSent": 300,
          "totalSuccess": 290,
          "totalFailed": 10
        },
        "WHATSAPP": {
          "totalSent": 200,
          "totalSuccess": 190,
          "totalFailed": 10
        }
      }
    }
  ]
}
```

#### Get Recent Messages

**Endpoint:** `GET /admin/api/messages/recent`

**Query Parameters:**
- `limit` (optional): Number of messages (default: 50)

```bash
curl "http://localhost:8080/admin/api/messages/recent?limit=50"
```

#### Get Failed Messages

**Endpoint:** `GET /admin/api/messages/failed`

**Query Parameters:**
- `limit` (optional): Number of messages (default: 50)

```bash
curl "http://localhost:8080/admin/api/messages/failed?limit=50"
```

## Status Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 202 | Accepted (notification queued) |
| 400 | Bad Request |
| 401 | Unauthorized (invalid API key) |
| 404 | Not Found |
| 500 | Internal Server Error |

## Message Statuses

| Status | Description |
|--------|-------------|
| PENDING | Message queued, waiting to be sent |
| SCHEDULED | Message scheduled for future delivery |
| SENT | Message sent to provider |
| DELIVERED | Message successfully delivered |
| FAILED | Message failed to send |
| BOUNCED | Email bounced |
| REJECTED | Message rejected by provider |

## Error Responses

```json
{
  "status": "BAD_REQUEST",
  "message": "Invalid request parameters",
  "timestamp": "2024-01-01T10:00:00"
}
```

## Rate Limits

Currently, there are no rate limits enforced. For production, consider implementing:
- Per-site rate limits
- Global rate limits
- Burst protection

## Webhooks (Future)

Webhook support for delivery status callbacks is planned for future releases.

