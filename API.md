# API Reference

Complete API reference for the Notification System with practical examples and developer tips.

## Table of Contents

- [Authentication](#authentication)
- [Site Management](#site-management)
- [Notifications](#notifications)
- [Message Logs](#message-logs)
- [Metrics](#metrics)
- [Admin Dashboard](#admin-dashboard)
- [Error Handling](#error-handling)

## Authentication

All API endpoints (except site registration) require authentication via the `X-Site-Key` header.

**Example:**
```bash
curl -H "X-Site-Key: your-api-key" \
  http://localhost:8080/api/v1/metrics/site/summary
```

**⚠️ Important:** API keys are generated once during site registration and shown only once. Store them securely!

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
  "siteId": "550e8400-e29b-41d4-a716-446655440000",
  "siteName": "my-frappe-site",
  "apiKey": "cg_abc123def456ghi789jkl012mno345pqr678stu901vwx234yz",
  "message": "Site registered successfully"
}
```

**⚠️ CRITICAL:** Save the `apiKey` immediately - it's only shown once! If lost, you'll need to regenerate it via admin dashboard.

**Developer Tip:** Store API keys in environment variables or secure secret management:
```bash
export SITE_API_KEY="cg_abc123def456ghi789jkl012mno345pqr678stu901vwx234yz"
```

## Notifications

### Send Single Notification

**Endpoint:** `POST /api/v1/notifications/send`

**Headers:**
- `X-Site-Key`: Your API key (required)
- `Content-Type`: application/json (required)

#### Email Notification

**Basic Email:**
```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Welcome!",
    "body": "Welcome to our service"
  }'
```

**HTML Email:**
```bash
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: your-api-key" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Welcome!",
    "body": "<h1>Welcome!</h1><p>Welcome to our service</p>",
    "isHtml": true,
    "fromEmail": "noreply@yourdomain.com",
    "fromName": "Your Company"
  }'
```

**Developer Tip:** Always set `isHtml: true` when sending HTML content. Plain text emails don't need this flag.

#### WhatsApp Text Message

**Simple Text:**
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

**⚠️ Note:** Phone numbers must include country code (e.g., `+1234567890` for US).

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

**Supported Image Formats:** JPG, PNG, GIF, WebP

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

**Supported Document Formats:** PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX

**Response:**
```json
{
  "messageId": "MSG-abc123",
  "status": "PENDING",
  "message": "Notification queued successfully"
}
```

**Developer Tip:** The API returns immediately with status `PENDING`. Check message logs to track delivery status.

### Send Bulk Notifications

**Endpoint:** `POST /api/v1/notifications/send/bulk`

Send multiple notifications in a single request.

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
      },
      {
        "channel": "EMAIL",
        "recipient": "user2@example.com",
        "subject": "Hello",
        "body": "Message 3"
      }
    ]
  }'
```

**Response:**
```json
{
  "totalQueued": 3,
  "messageIds": ["MSG-abc123", "MSG-def456", "MSG-ghi789"],
  "message": "Bulk notifications queued successfully"
}
```

**Developer Tip:** Bulk requests are processed asynchronously. Each notification is queued independently, so some may succeed while others fail.

### Schedule Notification

**Endpoint:** `POST /api/v1/notifications/schedule`

Schedule a notification for future delivery.

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

**Date Format:** ISO 8601 format (UTC): `YYYY-MM-DDTHH:mm:ssZ`

**Developer Tip:** Scheduled notifications are stored with status `SCHEDULED` and processed by a scheduled job.

## Message Logs

### List Messages

**Endpoint:** `GET /api/v1/messages/logs`

Retrieve message logs with filtering and pagination.

**Query Parameters:**
- `status` (optional): Filter by status (`PENDING`, `SENT`, `DELIVERED`, `FAILED`, `BOUNCED`, `REJECTED`)
- `channel` (optional): Filter by channel (`EMAIL`, `WHATSAPP`)
- `startDate` (optional): Start date (YYYY-MM-DD)
- `endDate` (optional): End date (YYYY-MM-DD)
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20, max: 100)

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
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "messageId": "MSG-ABC123",
      "siteId": "550e8400-e29b-41d4-a716-446655440001",
      "siteName": "My Site",
      "channel": "EMAIL",
      "status": "DELIVERED",
      "recipient": "user@example.com",
      "subject": "Welcome",
      "body": "Welcome message content",
      "fromEmail": "noreply@example.com",
      "fromName": "Notification Service",
      "isHtml": true,
      "sentAt": "2024-01-01T10:00:00Z",
      "deliveredAt": "2024-01-01T10:00:05Z",
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

**Developer Tip:** Use pagination for large result sets. Combine filters to narrow down results efficiently.

### Get Specific Message

**Endpoint:** `GET /api/v1/messages/logs/{messageId}`

Retrieve details of a specific message.

```bash
curl -H "X-Site-Key: your-api-key" \
  "http://localhost:8080/api/v1/messages/logs/MSG-ABC123"
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "messageId": "MSG-ABC123",
  "siteId": "550e8400-e29b-41d4-a716-446655440001",
  "siteName": "My Site",
  "channel": "EMAIL",
  "status": "DELIVERED",
  "recipient": "user@example.com",
  "subject": "Welcome",
  "body": "Welcome message content",
  "errorMessage": null,
  "sentAt": "2024-01-01T10:00:00Z",
  "deliveredAt": "2024-01-01T10:00:05Z",
  "createdAt": "2024-01-01T09:59:50Z"
}
```

**Developer Tip:** Use this endpoint to check delivery status after sending a notification. Poll until status changes from `PENDING` to `DELIVERED` or `FAILED`.

## Metrics

### Get Site Summary

**Endpoint:** `GET /api/v1/metrics/site/summary`

Get overall metrics for your site.

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

**Developer Tip:** Use this endpoint for dashboard displays. Metrics are aggregated daily, so recent data may not be included immediately.

### Get Daily Metrics

**Endpoint:** `GET /api/v1/metrics/site/daily`

Get daily metrics for a date range.

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

**Developer Tip:** Use this for time-series charts. Maximum date range is 90 days.

### Get Message Statistics

**Endpoint:** `GET /api/v1/messages/stats`

Get detailed message statistics.

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

**⚠️ Note:** Admin dashboard currently has no authentication. Add authentication in production!

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
      "siteId": "550e8400-e29b-41d4-a716-446655440000",
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
- `limit` (optional): Number of messages (default: 50, max: 100)

```bash
curl "http://localhost:8080/admin/api/messages/recent?limit=50"
```

#### Get Failed Messages

**Endpoint:** `GET /admin/api/messages/failed`

**Query Parameters:**
- `limit` (optional): Number of messages (default: 50, max: 100)

```bash
curl "http://localhost:8080/admin/api/messages/failed?limit=50"
```

## Error Handling

### Status Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 202 | Accepted (notification queued) |
| 400 | Bad Request (invalid parameters) |
| 401 | Unauthorized (invalid or missing API key) |
| 404 | Not Found (message or resource not found) |
| 500 | Internal Server Error |

### Error Response Format

```json
{
  "status": "BAD_REQUEST",
  "message": "Invalid request parameters",
  "timestamp": "2024-01-01T10:00:00Z",
  "errors": [
    {
      "field": "recipient",
      "message": "Recipient email is invalid"
    }
  ]
}
```

### Common Errors

**401 Unauthorized:**
```json
{
  "status": "UNAUTHORIZED",
  "message": "Invalid or missing API key",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

**Solution:** Verify your `X-Site-Key` header is correct and the site is active.

**400 Bad Request:**
```json
{
  "status": "BAD_REQUEST",
  "message": "Invalid channel. Supported channels: EMAIL, WHATSAPP",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

**Solution:** Check request payload against API documentation.

**500 Internal Server Error:**
```json
{
  "status": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

**Solution:** Check server logs and contact support if issue persists.

### Message Statuses

| Status | Description |
|--------|-------------|
| `PENDING` | Message queued, waiting to be sent |
| `SCHEDULED` | Message scheduled for future delivery |
| `SENT` | Message sent to provider |
| `DELIVERED` | Message successfully delivered |
| `FAILED` | Message failed to send (after retries) |
| `BOUNCED` | Email bounced (email-specific) |
| `REJECTED` | Message rejected by provider |

**Developer Tip:** Monitor `FAILED` status messages to identify issues with recipients or provider configuration.

## Rate Limits

Currently, there are no rate limits enforced. For production, consider implementing:
- Per-site rate limits
- Global rate limits
- Burst protection

## Best Practices

1. **Store API Keys Securely**: Never commit API keys to version control. Use environment variables or secret management.

2. **Handle Errors Gracefully**: Always check response status codes and handle errors appropriately.

3. **Use Bulk Endpoints**: For sending multiple notifications, use bulk endpoints instead of multiple individual requests.

4. **Monitor Message Status**: Poll message logs to track delivery status, especially for critical notifications.

5. **Validate Recipients**: Validate email addresses and phone numbers before sending to avoid failures.

6. **Use Appropriate Channels**: Choose the right channel (EMAIL vs WHATSAPP) based on message urgency and recipient preference.

7. **Set Proper Headers**: Always include `Content-Type: application/json` and `X-Site-Key` headers.

8. **Handle Async Nature**: Remember that notifications are processed asynchronously. The API returns immediately with `PENDING` status.

## Webhooks (Future)

Webhook support for delivery status callbacks is planned for future releases. This will allow real-time notifications when message status changes.

## SDK Examples

### JavaScript/Node.js

```javascript
const axios = require('axios');

const API_BASE_URL = 'http://localhost:8080/api/v1';
const API_KEY = 'your-api-key';

async function sendEmail(recipient, subject, body) {
  try {
    const response = await axios.post(
      `${API_BASE_URL}/notifications/send`,
      {
        channel: 'EMAIL',
        recipient: recipient,
        subject: subject,
        body: body,
        isHtml: true
      },
      {
        headers: {
          'X-Site-Key': API_KEY,
          'Content-Type': 'application/json'
        }
      }
    );
    return response.data;
  } catch (error) {
    console.error('Error sending email:', error.response?.data || error.message);
    throw error;
  }
}
```

### Python

```python
import requests

API_BASE_URL = 'http://localhost:8080/api/v1'
API_KEY = 'your-api-key'

def send_email(recipient, subject, body):
    response = requests.post(
        f'{API_BASE_URL}/notifications/send',
        json={
            'channel': 'EMAIL',
            'recipient': recipient,
            'subject': subject,
            'body': body,
            'isHtml': True
        },
        headers={
            'X-Site-Key': API_KEY,
            'Content-Type': 'application/json'
        }
    )
    response.raise_for_status()
    return response.json()
```

### Java

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

public class NotificationClient {
    private static final String API_BASE_URL = "http://localhost:8080/api/v1";
    private static final String API_KEY = "your-api-key";
    
    public NotificationResponse sendEmail(String recipient, String subject, String body) {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Site-Key", API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        NotificationRequest request = NotificationRequest.builder()
            .channel("EMAIL")
            .recipient(recipient)
            .subject(subject)
            .body(body)
            .isHtml(true)
            .build();
        
        HttpEntity<NotificationRequest> entity = new HttpEntity<>(request, headers);
        
        return restTemplate.postForObject(
            API_BASE_URL + "/notifications/send",
            entity,
            NotificationResponse.class
        );
    }
}
```
