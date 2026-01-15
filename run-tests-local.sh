#!/bin/bash

# Script to run integration tests locally using Docker
# Usage: ./run-tests-local.sh [clean]

set -euo pipefail

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

CLEAN=${1:-false}

echo -e "${GREEN}Starting Docker services for testing...${NC}"

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo -e "${RED}✗${NC} Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if port 5432 is already in use
if lsof -i :5432 &> /dev/null || netstat -tuln 2>/dev/null | grep -q ":5432 "; then
    echo -e "${YELLOW}⚠${NC}  Port 5432 is already in use."
    echo "   This might be a local PostgreSQL instance."
    echo "   Please stop it or use a different port."
    echo ""
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Exiting. To stop local PostgreSQL: sudo systemctl stop postgresql (or equivalent)"
        exit 1
    fi
fi

# Stop and remove existing test containers if clean is requested
if [ "$CLEAN" = "clean" ] || [ "$CLEAN" = "true" ]; then
    echo "Cleaning up existing test containers..."
    docker compose -f docker-compose.test.yml down -v 2>/dev/null || true
fi

# Start test services
echo "Starting PostgreSQL, Zookeeper, and Kafka..."
if docker compose -f docker-compose.test.yml up -d; then
    echo -e "${GREEN}✓${NC} Services started"
else
    echo -e "${RED}✗${NC} Failed to start services"
    exit 1
fi

# Wait for services to be ready
echo "Waiting for services to be ready..."
MAX_WAIT=60
WAIT_COUNT=0

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if docker exec notification-postgres-test pg_isready -U notification_user -d notification_db &> /dev/null; then
        echo -e "${GREEN}✓${NC} PostgreSQL is ready"
        break
    fi
    echo -n "."
    sleep 1
    ((WAIT_COUNT++))
done

if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
    echo -e "\n${RED}✗${NC} PostgreSQL did not become ready in time"
    docker compose -f docker-compose.test.yml logs postgres
    exit 1
fi

# Wait a bit more for Kafka
echo "Waiting for Kafka..."
sleep 10

# Set environment variables for tests
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/notification_db"
export SPRING_DATASOURCE_USERNAME="notification_user"
export SPRING_DATASOURCE_PASSWORD="notification_pass"
export SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"

echo ""
echo -e "${GREEN}Running integration tests...${NC}"
echo "Environment variables:"
echo "  SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"
echo "  SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME"
echo "  SPRING_KAFKA_BOOTSTRAP_SERVERS=$SPRING_KAFKA_BOOTSTRAP_SERVERS"
echo ""

# Run tests
if mvn clean verify -DskipTests=false; then
    echo ""
    echo -e "${GREEN}✓${NC} All tests passed!"
    
    # Ask if user wants to keep services running
    echo ""
    read -p "Keep Docker services running? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Stopping test services..."
        docker compose -f docker-compose.test.yml down
        echo -e "${GREEN}✓${NC} Services stopped"
    else
        echo "Services are still running. Stop them with: docker compose -f docker-compose.test.yml down"
    fi
else
    echo ""
    echo -e "${RED}✗${NC} Tests failed"
    echo ""
    echo "To view service logs:"
    echo "  docker compose -f docker-compose.test.yml logs postgres"
    echo "  docker compose -f docker-compose.test.yml logs kafka"
    echo ""
    echo "To stop services:"
    echo "  docker compose -f docker-compose.test.yml down"
    exit 1
fi

