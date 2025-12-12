#!/bin/bash

echo "=========================================="
echo "Notification System - Local Setup"
echo "=========================================="
echo ""

# Check prerequisites
echo "Checking prerequisites..."
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running"
    echo "Please start Docker Desktop or run: sudo systemctl start docker"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed"
    exit 1
fi

echo "✓ Prerequisites check passed"
echo ""

# Build common-proto
echo "Building common-proto module..."
cd common-proto
mvn clean install -DskipTests
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to build common-proto"
    exit 1
fi
cd ..
echo "✓ common-proto built successfully"
echo ""

# Start infrastructure
echo "Starting infrastructure (PostgreSQL, Kafka, Zookeeper)..."
docker-compose up -d postgres zookeeper kafka

echo "Waiting for services to be ready..."
sleep 10

# Check if services are up
if ! docker-compose ps | grep -q "postgres.*Up"; then
    echo "WARNING: PostgreSQL might not be ready"
fi

if ! docker-compose ps | grep -q "kafka.*Up"; then
    echo "WARNING: Kafka might not be ready"
fi

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Set environment variables:"
echo "   export WASENDER_API_KEY=your-wasender-api-key"
echo ""
echo "2. Start the services (in separate terminals):"
echo "   cd notification-api && mvn spring-boot:run"
echo "   cd email-worker && mvn spring-boot:run"
echo "   cd whatsapp-worker && mvn spring-boot:run"
echo ""
echo "3. Register a site:"
echo "   curl -X POST http://localhost:8080/api/v1/site/register \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"siteName\": \"test-site\"}'"
echo ""

