#!/bin/bash

set -e

echo "=========================================="
echo "Notification System - Docker Compose Start"
echo "=========================================="
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "⚠️  .env file not found. Creating from .env.example..."
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "✅ Created .env file. Please edit it with your API keys."
    else
        echo "❌ .env.example not found. Please create .env manually."
        exit 1
    fi
fi

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "❌ Docker daemon is not running"
    exit 1
fi

# Check docker compose
if ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose is not installed"
    exit 1
fi

echo "✅ Prerequisites check passed"
echo ""

# Build common-proto first (if needed)
echo "Building common-proto module..."
cd common-proto
mvn clean install -DskipTests -q
cd ..
echo "✅ common-proto built"
echo ""

# Start infrastructure first
echo "Starting infrastructure (PostgreSQL, Kafka, Zookeeper)..."
docker compose up -d postgres zookeeper kafka

echo "Waiting for infrastructure to be ready..."
sleep 15

# Check infrastructure health
echo "Checking infrastructure health..."
docker compose ps postgres kafka zookeeper

# Build and start application services
echo ""
echo "Building and starting application services..."
docker compose build --no-cache notification-api email-worker whatsapp-worker
docker compose up -d notification-api email-worker whatsapp-worker

echo ""
echo "Waiting for services to start..."
sleep 20

echo ""
echo "=========================================="
echo "✅ Services Started!"
echo "=========================================="
echo ""
docker compose ps
echo ""
echo "Service URLs:"
echo "  - Notification API: http://localhost:8080"
echo "  - Email Worker: http://localhost:8081/actuator/health"
echo "  - WhatsApp Worker: http://localhost:8082/actuator/health"
echo "  - Kafka UI: http://localhost:8089"
echo ""
echo "View logs: docker compose logs -f"
echo "Stop services: docker compose down"
echo ""



