# Docker Compose Setup Guide

## Quick Start

### 1. Set Environment Variables

Create a `.env` file in the root directory:

```bash
cp .env.example .env
# Edit .env and add your API keys
```

Or export them:
```bash
export WASENDER_API_KEY=your-wasender-api-key-here
export SENDGRID_API_KEY=your-sendgrid-api-key  # Optional
```

### 2. Start All Services

```bash
docker compose up -d
```

This will:
- ✅ Start PostgreSQL (port 5432)
- ✅ Start Zookeeper (port 2181)
- ✅ Start Kafka (ports 9092, 9093)
- ✅ Start Kafka UI (port 8089)
- ✅ Build and start Notification API (port 8080)
- ✅ Build and start Email Worker (port 8081)
- ✅ Build and start WhatsApp Worker (port 8082)

### 3. Check Service Status

```bash
docker compose ps
```

All services should show "healthy" status.

### 4. View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f notification-api
docker compose logs -f whatsapp-worker
docker compose logs -f email-worker
```

### 5. Stop Services

```bash
docker compose down
```

To also remove volumes:
```bash
docker compose down -v
```

## Service URLs

- **Notification API**: http://localhost:8080
- **Email Worker**: http://localhost:8081/actuator/health
- **WhatsApp Worker**: http://localhost:8082/actuator/health
- **Kafka UI**: http://localhost:8089
- **PostgreSQL**: localhost:5432

## Health Checks

All services have health checks configured. Check status:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

## Building Services

To rebuild services after code changes:

```bash
docker compose build
docker compose up -d
```

Or rebuild specific service:
```bash
docker compose build notification-api
docker compose up -d notification-api
```

## Troubleshooting

### Services won't start

1. Check Docker is running: `docker ps`
2. Check ports are available: `netstat -tulpn | grep -E '8080|8081|8082|5432|9092'`
3. Check logs: `docker compose logs [service-name]`

### Database connection issues

```bash
# Connect to database
docker exec -it notification-postgres psql -U notification_user -d notification_db

# Check tables
\dt

# Check message logs
SELECT * FROM message_logs ORDER BY created_at DESC LIMIT 10;
```

### Kafka issues

```bash
# List topics
docker exec -it notification-kafka kafka-topics --list --bootstrap-server localhost:9092

# Check consumer groups
docker exec -it notification-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

### Rebuild from scratch

```bash
docker compose down -v
docker compose build --no-cache
docker compose up -d
```

## Environment Variables

The following environment variables can be set in `.env` file or exported:

- `WASENDER_API_KEY` - Required for WhatsApp notifications
- `SENDGRID_API_KEY` - Required for email notifications
- `SENDGRID_FROM_EMAIL` - Email sender address
- `SENDGRID_FROM_NAME` - Email sender name

## Production Considerations

1. **Security**: Change default passwords in production
2. **Volumes**: Use named volumes for persistent data
3. **Networks**: Use custom Docker networks for isolation
4. **Resource Limits**: Add memory/CPU limits for production
5. **Secrets**: Use Docker secrets or external secret management
6. **Monitoring**: Add Prometheus/Grafana for monitoring
7. **Logging**: Configure centralized logging (ELK stack)



