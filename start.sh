#!/bin/bash

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Notification System - Docker Compose Start"
echo "=========================================="
echo ""

# Check prerequisites
check_prerequisite() {
    local cmd=$1
    local name=$2
    
    if ! command -v "$cmd" &> /dev/null; then
        echo -e "${RED}✗${NC} $name is not installed"
        return 1
    fi
    return 0
}

if ! check_prerequisite docker "Docker"; then
    exit 1
fi

if ! docker info &> /dev/null; then
    echo -e "${RED}✗${NC} Docker daemon is not running"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo -e "${RED}✗${NC} Docker Compose is not installed"
    exit 1
fi

echo -e "${GREEN}✓${NC} Prerequisites check passed"
echo ""

# Check/create .env file
if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠${NC}  .env file not found"
    if [ -f .env.example ]; then
        cp .env.example .env
        echo -e "${GREEN}✓${NC} Created .env file from .env.example"
        echo -e "${YELLOW}⚠${NC}  Please edit .env file with your API keys before continuing"
    else
        echo -e "${RED}✗${NC} .env.example not found. Please create .env manually"
        exit 1
    fi
fi

# Build common-proto
echo "Building common-proto module..."
if cd common-proto && mvn clean install -DskipTests -q; then
    cd ..
    echo -e "${GREEN}✓${NC} common-proto built successfully"
else
    echo -e "${RED}✗${NC} Failed to build common-proto"
    exit 1
fi
echo ""

# Start infrastructure
echo "Starting infrastructure services (PostgreSQL, Kafka, Zookeeper)..."
if docker compose up -d postgres zookeeper kafka; then
    echo -e "${GREEN}✓${NC} Infrastructure services started"
else
    echo -e "${RED}✗${NC} Failed to start infrastructure services"
    exit 1
fi

echo "Waiting for infrastructure to be ready..."
sleep 15

# Check infrastructure health
if docker compose ps postgres kafka zookeeper | grep -q "Up"; then
    echo -e "${GREEN}✓${NC} Infrastructure is healthy"
else
    echo -e "${YELLOW}⚠${NC}  Some infrastructure services may not be ready"
fi
echo ""

# Build application services
echo "Building application services..."
if docker compose build --no-cache notification-api email-worker whatsapp-worker; then
    echo -e "${GREEN}✓${NC} Application services built successfully"
else
    echo -e "${RED}✗${NC} Failed to build application services"
    exit 1
fi

# Start application services
echo "Starting application services..."
if docker compose up -d notification-api email-worker whatsapp-worker; then
    echo -e "${GREEN}✓${NC} Application services started"
else
    echo -e "${RED}✗${NC} Failed to start application services"
    exit 1
fi

echo "Waiting for services to start..."
sleep 20

echo ""
echo "=========================================="
echo -e "${GREEN}✓ Services Started Successfully!${NC}"
echo "=========================================="
echo ""
docker compose ps
echo ""
echo "Service URLs:"
echo "  - Notification API: http://localhost:8080"
echo "  - Email Worker: http://localhost:8081/actuator/health"
echo "  - WhatsApp Worker: http://localhost:8083/actuator/health"
echo "  - Admin Dashboard: http://localhost:8080/admin/dashboard"
echo "  - Kafka UI: http://localhost:8089"
echo ""
echo "Useful commands:"
echo "  View logs:      docker compose logs -f"
echo "  Stop services:  docker compose down"
echo "  Restart:        docker compose restart"
echo ""



