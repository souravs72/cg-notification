# Admin Dashboard

## Overview

The Admin Dashboard provides a comprehensive view of all Frappe sites and their notification metrics.

## Access

**Dashboard URL:** http://localhost:8080/admin/dashboard

**API Endpoint:** http://localhost:8080/admin/api/metrics (JSON)

## Features

### 1. Summary Cards
- **Total Sites**: Number of registered Frappe sites
- **Total Messages Sent**: Aggregate count across all sites
- **Successful Messages**: Messages successfully delivered
- **Failed Messages**: Messages that failed to deliver

### 2. Overall Success Rate
- Displays overall success rate percentage
- Color-coded: Green (≥95%), Yellow (≥80%), Red (<80%)

### 3. Visual Charts
- **Messages by Status**: Doughnut chart showing successful vs failed messages
- **Messages by Channel**: Bar chart showing Email vs WhatsApp distribution

### 4. Site-wise Metrics Table
For each Frappe site, displays:
- Site Name
- Total Messages Sent
- Successful Messages Count
- Failed Messages Count
- Success Rate (with color-coded badges)
- Email Channel Count
- WhatsApp Channel Count

## Auto-Refresh

The dashboard automatically refreshes every 30 seconds to show the latest metrics.

## Manual Refresh

Click the "Refresh" button in the top-right corner of the sites table to manually update the data.

## API Endpoint

You can also access the metrics data as JSON:

```bash
curl http://localhost:8080/admin/api/metrics
```

Response format:
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
          "channel": "EMAIL",
          "totalSent": 300,
          "totalSuccess": 290,
          "totalFailed": 10
        },
        "WHATSAPP": {
          "channel": "WHATSAPP",
          "totalSent": 200,
          "totalSuccess": 190,
          "totalFailed": 10
        }
      }
    }
  ]
}
```

## Security Note

⚠️ **Important**: The admin dashboard is currently accessible without authentication. For production use, you should:

1. Add authentication (Spring Security)
2. Restrict access to admin users only
3. Use HTTPS
4. Consider IP whitelisting

## Usage Example

1. Start the application:
   ```bash
   docker compose up -d
   ```

2. Wait for services to be healthy:
   ```bash
   docker compose ps
   ```

3. Access the dashboard:
   ```
   http://localhost:8080/admin/dashboard
   ```

4. View metrics for all registered Frappe sites in one place!

