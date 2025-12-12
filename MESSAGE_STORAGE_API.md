# Message Storage API Documentation

## Overview

All messages sent through the notification system are now stored in the database with complete content and status tracking.

## Message Storage Features

### ✅ Complete Content Storage
- **Basic Fields**: recipient, subject, body
- **WhatsApp Fields**: imageUrl, videoUrl, documentUrl, fileName, caption
- **Email Fields**: fromEmail, fromName, isHtml
- **Metadata**: Custom JSON metadata
- **Timestamps**: createdAt, updatedAt, sentAt, deliveredAt, scheduledAt

### ✅ Status Tracking
- **PENDING**: Message queued, waiting to be sent
- **SCHEDULED**: Message scheduled for future delivery
- **SENT**: Message sent to provider
- **DELIVERED**: Message successfully delivered
- **FAILED**: Message failed to send
- **BOUNCED**: Email bounced
- **REJECTED**: Message rejected by provider

### ✅ Statistics
- Total messages per site
- Messages by status
- Average messages per day
- Success rate calculation

## API Endpoints

### 1. List Messages

**GET** `/api/v1/messages/logs`

Query messages with filters and pagination.

**Headers:**
- `X-Site-Key`: Your API key

**Query Parameters:**
- `status` (optional): Filter by status (PENDING, SENT, DELIVERED, FAILED, etc.)
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

### 2. Get Specific Message

**GET** `/api/v1/messages/logs/{messageId}`

Get details of a specific message.

**Headers:**
- `X-Site-Key`: Your API key

**Example:**
```bash
curl -H "X-Site-Key: your-api-key" \
  "http://localhost:8080/api/v1/messages/logs/MSG-ABC123"
```

### 3. Get Message Statistics

**GET** `/api/v1/messages/stats`

Get aggregated statistics for your site.

**Headers:**
- `X-Site-Key`: Your API key

**Example:**
```bash
curl -H "X-Site-Key: your-api-key" \
  "http://localhost:8080/api/v1/messages/stats"
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

## Database Schema

The `message_logs` table now includes:

```sql
- id (UUID)
- message_id (VARCHAR, unique)
- site_id (UUID)
- channel (notification_channel enum)
- status (delivery_status enum)
- recipient (VARCHAR)
- subject (VARCHAR)
- body (TEXT)
- error_message (TEXT)
- retry_count (INTEGER)
- sent_at (TIMESTAMP)
- delivered_at (TIMESTAMP)
- scheduled_at (TIMESTAMP)
- image_url (VARCHAR)
- video_url (VARCHAR)
- document_url (VARCHAR)
- file_name (VARCHAR)
- caption (TEXT)
- from_email (VARCHAR)
- from_name (VARCHAR)
- is_html (BOOLEAN)
- metadata (JSONB)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)
```

## Migration

If you have an existing database, run the migration script:

```bash
docker exec -i notification-postgres psql -U notification_user -d notification_db < deployment/migration-add-message-fields.sql
```

Or connect to the database and run the SQL commands manually.
