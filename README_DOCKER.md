# Docker Compose - Complete Setup

## ✅ Yes, Docker Compose is Complete!

Running `docker compose up` will start **all services and dependencies** automatically.

## What Gets Started

### Infrastructure Services (Automatic)
1. **PostgreSQL** (port 5432)
   - Database: `notification_db`
   - User: `notification_user`
   - Password: `notification_pass`
   - Auto-initializes schema from `deployment/init-db.sql`

2. **Zookeeper** (port 2181)
   - Required for Kafka coordination

3. **Kafka** (ports 9092, 9093)
   - Message broker for async processing
   - Auto-creates topics

4. **Kafka UI** (port 8089)
   - Web interface for monitoring Kafka
   - Access at: http://localhost:8089

### Application Services (Auto-built and Started)
5. **Notification API** (port 8080)
   - REST API gateway
   - Auto-builds from source
   - Waits for PostgreSQL and Kafka to be healthy

6. **Email Worker** (port 8081)
   - Processes email notifications via SendGrid
   - Auto-builds from source
   - Waits for PostgreSQL and Kafka

7. **WhatsApp Worker** (port 8082)
   - Processes WhatsApp notifications via WASender
   - Auto-builds from source
   - Waits for PostgreSQL and Kafka

## Quick Start

### Option 1: Using the Start Script (Recommended)

```bash
# Make sure .env file exists with your API keys
cp .env.example .env
# Edit .env with your keys

# Run the start script
./start.sh
```

### Option 2: Manual Docker Compose

```bash
# 1. Set environment variables
export WASENDER_API_KEY=your-wasender-api-key-here
export SENDGRID_API_KEY=your-sendgrid-api-key  # Optional

# 2. Build common-proto first
cd common-proto && mvn clean install -DskipTests && cd ..

# 3. Start everything
docker compose up -d

# 4. Check status
docker compose ps
```

## What Happens When You Run `docker compose up`

1. ✅ **Pulls Images**: Downloads PostgreSQL, Kafka, Zookeeper images
2. ✅ **Builds Applications**: Compiles Java code and creates Docker images
3. ✅ **Starts Infrastructure**: PostgreSQL, Kafka, Zookeeper start first
4. ✅ **Waits for Health**: Services wait for dependencies to be healthy
5. ✅ **Starts Applications**: API and Workers start after infrastructure is ready
6. ✅ **Initializes Database**: Schema is created automatically
7. ✅ **Ready to Use**: All services are operational

## Service Dependencies

```
PostgreSQL ──┐
             ├──> Notification API
Zookeeper ───┤
             ├──> Email Worker
Kafka ───────┤
             └──> WhatsApp Worker
```

## Health Checks

All services have health checks:
- Infrastructure services: Basic connectivity checks
- Application services: HTTP health endpoint checks

Check health status:
```bash
docker compose ps
```

## Environment Variables

Create `.env` file (or export variables):

```bash
WASENDER_API_KEY=your-wasender-api-key-here
SENDGRID_API_KEY=your-sendgrid-api-key
SENDGRID_FROM_EMAIL=noreply@yourdomain.com
SENDGRID_FROM_NAME=Your Company
```

## Verification

After starting, verify everything works:

```bash
# 1. Check all services are running
docker compose ps

# 2. Check API health
curl http://localhost:8080/health

# 3. Register a site
curl -X POST http://localhost:8080/api/v1/site/register \
  -H "Content-Type: application/json" \
  -d '{"siteName": "test-site"}'

# 4. Send a test message (use API key from registration)
curl -X POST http://localhost:8080/api/v1/notifications/send \
  -H "Content-Type: application/json" \
  -H "X-Site-Key: YOUR_API_KEY" \
  -d '{
    "channel": "WHATSAPP",
    "recipient": "918576882906@s.whatsapp.net",
    "body": "Test message"
  }'
```

## Ports Used

- **5432**: PostgreSQL
- **2181**: Zookeeper
- **9092, 9093**: Kafka
- **8080**: Notification API
- **8081**: Email Worker
- **8082**: WhatsApp Worker
- **8089**: Kafka UI

## Data Persistence

- PostgreSQL data is stored in Docker volume: `cg-notification_postgres_data`
- Data persists across container restarts
- To reset: `docker compose down -v`

## Logs

View logs for all services:
```bash
docker compose logs -f
```

View logs for specific service:
```bash
docker compose logs -f notification-api
docker compose logs -f whatsapp-worker
docker compose logs -f email-worker
```

## Stopping Services

```bash
# Stop all services (keeps data)
docker compose down

# Stop and remove volumes (deletes data)
docker compose down -v
```

## Rebuilding After Code Changes

```bash
# Rebuild specific service
docker compose build notification-api
docker compose up -d notification-api

# Rebuild all services
docker compose build
docker compose up -d
```

## Troubleshooting

### Port Already in Use
If ports are already in use, modify `docker-compose.yml` to use different ports.

### Build Failures
Make sure `common-proto` is built first:
```bash
cd common-proto && mvn clean install -DskipTests && cd ..
```

### Services Not Starting
Check logs:
```bash
docker compose logs [service-name]
```

### Database Connection Issues
Verify PostgreSQL is healthy:
```bash
docker compose ps postgres
docker compose logs postgres
```

## Production Notes

For production deployment:
1. Change default passwords
2. Use Docker secrets for API keys
3. Configure resource limits
4. Set up monitoring and logging
5. Use external PostgreSQL/Kafka for high availability
6. Configure SSL/TLS
7. Set up backup strategies

## Summary

✅ **Yes, `docker compose up` will start everything!**

Just run:
```bash
docker compose up -d
```

And all services will:
- Build automatically
- Start in correct order
- Wait for dependencies
- Be ready to use

No manual steps required! 🚀



